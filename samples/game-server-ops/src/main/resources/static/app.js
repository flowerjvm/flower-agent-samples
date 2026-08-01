const state = {
    taskId: null,
    pollTimer: null,
    lastFinalMessage: null
};

const terminalStatuses = new Set(["SUCCEEDED", "FAILED", "CANCELLED"]);

document.addEventListener("DOMContentLoaded", () => {
    bindEvents();
    clearExecution();
    loadConfig();
    loadServers();
});

function bindEvents() {
    document.getElementById("task-form").addEventListener("submit", startTask);
    document.getElementById("cancel-button").addEventListener("click", cancelTask);
    document.getElementById("reset-button").addEventListener("click", resetDemo);
    document.querySelectorAll(".tab").forEach((button) => {
        button.addEventListener("click", () => activateTab(button.dataset.panel));
    });
}

async function loadConfig() {
    try {
        const config = await requestJson("/api/config");
        document.getElementById("model-name").textContent = config.model;
        document.getElementById("model-endpoint").textContent = config.endpoint;
        document.getElementById("credential-dot").classList.toggle("ready", config.credentialConfigured);
    } catch (error) {
        document.getElementById("model-name").textContent = "Configuration unavailable";
        document.getElementById("model-endpoint").textContent = error.message;
    }
}

async function loadServers() {
    try {
        renderServers(await requestJson("/api/servers"));
    } catch (error) {
        renderError(error.message);
    }
}

function renderServers(servers) {
    const target = document.getElementById("server-list");
    target.innerHTML = servers.map((server) => `
        <article class="server-card ${server.state === "DEGRADED" ? "degraded" : ""}">
            <header>
                <h3>${escapeHtml(server.serverId)}</h3>
                <span class="status-pill ${server.state === "HEALTHY" ? "success" : "failed"}">${escapeHtml(server.state)}</span>
            </header>
            <p class="server-region">${escapeHtml(server.region)}</p>
            <div class="server-metrics">
                <span>${server.activePlayers} players</span>
                <span>${server.restartCount} restarts</span>
            </div>
        </article>
    `).join("");
}

async function startTask(event) {
    event.preventDefault();
    const input = document.getElementById("task-input");
    const message = input.value.trim();
    if (!message) {
        return;
    }
    stopPolling();
    state.lastFinalMessage = null;
    setBusy(true);
    appendMessage("Operator", message, "user-message");
    clearExecution();
    try {
        const task = await requestJson("/api/tasks", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({message})
        });
        state.taskId = task.taskId;
        renderTask(task);
        state.pollTimer = window.setInterval(pollTask, 600);
    } catch (error) {
        setBusy(false);
        renderError(error.message);
    }
}

async function pollTask() {
    if (!state.taskId) {
        return;
    }
    try {
        const task = await requestJson(`/api/tasks/${encodeURIComponent(state.taskId)}`);
        renderTask(task);
        if (terminalStatuses.has(task.status)) {
            stopPolling();
            setBusy(false);
            await loadServers();
            appendTerminalMessage(task);
        }
    } catch (error) {
        stopPolling();
        setBusy(false);
        renderError(error.message);
    }
}

async function cancelTask() {
    if (!state.taskId) {
        return;
    }
    try {
        const task = await requestJson(`/api/tasks/${encodeURIComponent(state.taskId)}/cancel`, {method: "POST"});
        renderTask(task);
    } catch (error) {
        renderError(error.message);
    }
}

async function resetDemo() {
    try {
        const servers = await requestJson("/api/demo/reset", {method: "POST"});
        stopPolling();
        state.taskId = null;
        state.lastFinalMessage = null;
        setBusy(false);
        clearExecution();
        document.getElementById("chat-log").innerHTML = `
            <div class="message agent-message"><span class="message-author">Agent</span><p>Ready.</p></div>`;
        renderServers(servers);
    } catch (error) {
        renderError(error.message);
    }
}

function renderTask(task) {
    renderStatus(task.status);
    renderAgentTimeline(task.agentAttempts);
    renderActionTimeline(task.actions, task.audit);
    renderReport(task.report, task.findings);

    const turns = task.agentAttempts.reduce((sum, attempt) => sum + attempt.turns, 0);
    const tools = task.agentAttempts.reduce((sum, attempt) => sum + attempt.toolCalls, 0);
    document.getElementById("usage-summary").textContent = `${turns} turns / ${tools} tools`;
}

function renderStatus(status) {
    const target = document.getElementById("task-status");
    target.textContent = status;
    target.className = `status-pill ${statusClass(status)}`;
}

function renderAgentTimeline(attempts) {
    const target = document.getElementById("agent-timeline");
    const entries = [];
    attempts.forEach((attempt, index) => {
        entries.push(timelineItem(
            `Agent attempt ${index + 1}`,
            `${attempt.status} / ${attempt.turns} turns / ${attempt.inputTokens + attempt.outputTokens} tokens`,
            statusClass(attempt.status)));
        attempt.transcript.forEach((message) => {
            if (message.toolCalls.length) {
                message.toolCalls.forEach((call) => entries.push(timelineItem(
                    call.toolName,
                    JSON.stringify(call.arguments, null, 2),
                    "tool")));
            } else if (message.role === "TOOL") {
                entries.push(timelineItem(
                    `${message.toolName} result`,
                    shorten(message.content, 900),
                    message.metadata.status === "SUCCEEDED" ? "success" : "failed"));
            } else if (message.role === "ASSISTANT" && message.content) {
                entries.push(timelineItem("Assistant response", shorten(message.content, 900), ""));
            }
        });
    });
    target.innerHTML = entries.length ? entries.join("") : emptyTimeline("Waiting for AgentRun.");
}

function renderActionTimeline(actions, audit) {
    const target = document.getElementById("action-timeline");
    const entries = [];
    actions.forEach((action) => entries.push(timelineItem(
        `${action.actionId} / ${action.status}`,
        `${action.serverId}\n${action.code}${action.message ? `\n${action.message}` : ""}`,
        action.status === "SUCCEEDED" ? "success" : "failed")));
    audit.forEach((event) => entries.push(timelineItem(
        event.type,
        JSON.stringify(event.payload, null, 2),
        "action")));
    target.innerHTML = entries.length ? entries.join("") : emptyTimeline("No governed action requested.");
}

function renderReport(report, findings) {
    const target = document.getElementById("report-view");
    if (!report) {
        target.className = "report-view empty-state";
        target.textContent = "No validated report.";
        return;
    }
    target.className = "report-view";
    const risks = report.residualRisks.length ? report.residualRisks : ["None recorded"];
    target.innerHTML = `
        <h3>${escapeHtml(report.summary)}</h3>
        <div class="report-grid">
            ${reportMetric("Outcome", report.outcome)}
            ${reportMetric("Server", report.finalState.serverId)}
            ${reportMetric("Final state", `${report.finalState.state} / ${report.finalState.restartCount} restarts`)}
        </div>
        <strong>Evidence</strong>
        <ul class="report-list">${report.evidence.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
        <strong>Actions</strong>
        <ul class="report-list">${report.actions.length
            ? report.actions.map((item) => `<li>${escapeHtml(item.actionId)} / ${escapeHtml(item.status)} / ${escapeHtml(item.reason)}</li>`).join("")
            : "<li>No action executed</li>"}</ul>
        <strong>Residual risks</strong>
        <ul class="report-list">${risks.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
        ${findings.length ? `<p class="empty-state">Harness findings: ${findings.length}</p>` : ""}
    `;
}

function appendTerminalMessage(task) {
    const latestAttempt = task.agentAttempts.at(-1);
    const finalAssistant = latestAttempt?.transcript.filter((message) => message.role === "ASSISTANT" && message.content).at(-1);
    const content = task.report?.summary || finalAssistant?.content || task.terminalReason || task.status;
    if (state.lastFinalMessage === content) {
        return;
    }
    state.lastFinalMessage = content;
    appendMessage("Agent", content, task.status === "SUCCEEDED" ? "agent-message" : "error-message");
}

function appendMessage(author, content, className) {
    const log = document.getElementById("chat-log");
    const node = document.createElement("div");
    node.className = `message ${className}`;
    node.innerHTML = `<span class="message-author">${escapeHtml(author)}</span><p>${escapeHtml(content)}</p>`;
    log.appendChild(node);
    log.scrollTop = log.scrollHeight;
}

function renderError(message) {
    appendMessage("System", message, "error-message");
}

function clearExecution() {
    renderStatus("IDLE");
    document.getElementById("usage-summary").textContent = "0 turns / 0 tools";
    document.getElementById("agent-timeline").innerHTML = emptyTimeline("Waiting for AgentRun.");
    document.getElementById("action-timeline").innerHTML = emptyTimeline("No governed action requested.");
    document.getElementById("report-view").className = "report-view empty-state";
    document.getElementById("report-view").textContent = "No validated report.";
}

function setBusy(busy) {
    document.getElementById("run-button").disabled = busy;
    document.getElementById("task-input").disabled = busy;
    document.getElementById("cancel-button").disabled = !busy;
    document.getElementById("reset-button").disabled = busy;
}

function stopPolling() {
    if (state.pollTimer) {
        window.clearInterval(state.pollTimer);
        state.pollTimer = null;
    }
}

function activateTab(panelId) {
    document.querySelectorAll(".tab").forEach((tab) => tab.classList.toggle("active", tab.dataset.panel === panelId));
    document.querySelectorAll(".tab-panel").forEach((panel) => panel.classList.toggle("active", panel.id === panelId));
}

function timelineItem(title, detail, className) {
    return `<li class="timeline-item ${className}">
        <div class="timeline-title">${escapeHtml(title)}</div>
        <pre class="timeline-detail">${escapeHtml(detail)}</pre>
    </li>`;
}

function emptyTimeline(message) {
    return `<li class="empty-state">${escapeHtml(message)}</li>`;
}

function reportMetric(label, value) {
    return `<div class="report-metric"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`;
}

function statusClass(status) {
    if (["SUCCEEDED", "COMPLETED", "HEALTHY"].includes(status)) {
        return "success";
    }
    if (["FAILED", "CANCELLED", "BUDGET_EXHAUSTED", "INTERRUPTED", "DEGRADED"].includes(status)) {
        return "failed";
    }
    return status === "IDLE" ? "neutral" : "running";
}

function shorten(value, limit) {
    if (!value || value.length <= limit) {
        return value || "";
    }
    return `${value.slice(0, limit)}\n...`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(body.error || `${response.status} ${response.statusText}`);
    }
    return body;
}
