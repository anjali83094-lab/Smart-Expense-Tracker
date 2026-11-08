const form = document.getElementById("transaction-form");
const transactionList = document.getElementById("transaction-list");
const message = document.getElementById("message");

const chartColors = [
    "#7048f5",
    "#16a875",
    "#fa4b68",
    "#f5a33b",
    "#26a6d9",
    "#399eb5",
    "#7185a7",
    "#ec75b2"
];

const money = amount =>
    "₹" + Number(amount).toLocaleString("en-IN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });

document.getElementById("current-date").textContent =
    new Date().toLocaleDateString("en-IN", {
        day: "numeric",
        month: "short",
        year: "numeric"
    });

let transactions = [];

async function loadTransactions() {
    try {
        const response = await fetch("/api/transactions");

        if (!response.ok) {
            throw new Error("Unable to load transactions.");
        }

        transactions = await response.json();

        renderTransactions();
        renderCharts();
        await loadSummary();

    } catch (error) {
        showMessage(error.message, true);
    }
}

function renderTransactions() {
    transactionList.innerHTML = "";

    document.getElementById("record-count").textContent =
        `${transactions.length} record${transactions.length === 1 ? "" : "s"}`;

    if (transactions.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 5;
        cell.className = "empty-state";
        cell.textContent = "No transactions yet. Add your first transaction!";

        row.appendChild(cell);
        transactionList.appendChild(row);
        return;
    }

    [...transactions].reverse().forEach(t => {
        const row = document.createElement("tr");

        const dateCell = document.createElement("td");
        dateCell.textContent = formatDate(t.date);
        row.appendChild(dateCell);

        const typeCell = document.createElement("td");
        const badge = document.createElement("span");

        badge.className =
            "type-badge " +
            (t.type === "INCOME" ? "type-income" : "type-expense");

        badge.textContent = t.type;
        typeCell.appendChild(badge);
        row.appendChild(typeCell);

        addTextCell(row, t.category);
        addTextCell(row, t.description);

        const amountCell = document.createElement("td");
        amountCell.className =
            "amount-cell " +
            (t.type === "INCOME" ? "amount-income" : "amount-expense");

        amountCell.textContent =
            (t.type === "INCOME" ? "+" : "−") + money(t.amount);

        row.appendChild(amountCell);

        transactionList.appendChild(row);
    });
}

function addTextCell(row, value) {
    const cell = document.createElement("td");
    cell.textContent = value ?? "";
    row.appendChild(cell);
}

function formatDate(dateText) {
    if (!dateText) return "";

    const parts = dateText.split("-");

    if (parts.length !== 3) return dateText;

    const date = new Date(
        Number(parts[0]),
        Number(parts[1]) - 1,
        Number(parts[2])
    );

    return date.toLocaleDateString("en-IN", {
        day: "2-digit",
        month: "short",
        year: "numeric"
    });
}

async function loadSummary() {
    const response = await fetch("/api/summary");

    if (!response.ok) {
        throw new Error("Unable to load financial summary.");
    }

    const summary = await response.json();

    document.getElementById("total-income").textContent =
        money(summary.income);

    document.getElementById("total-expenses").textContent =
        money(summary.expenses);

    document.getElementById("balance").textContent =
        money(summary.balance);

    document.getElementById("donut-total").textContent =
        money(summary.expenses);
}

/* Add a new transaction */

form.addEventListener("submit", async event => {
    event.preventDefault();

    const formData = new FormData(form);

    const transaction = {
        type: formData.get("type"),
        amount: formData.get("amount"),
        category: formData.get("category").trim(),
        description: formData.get("description").trim()
    };

    if (!transaction.category || !transaction.description) {
        showMessage("Please complete all fields.", true);
        return;
    }

    if (!Number.isFinite(Number(transaction.amount)) ||
        Number(transaction.amount) <= 0) {
        showMessage("Enter an amount greater than zero.", true);
        return;
    }

    const submitButton = form.querySelector('button[type="submit"]');
    submitButton.disabled = true;
    submitButton.textContent = "Adding transaction...";

    try {
        const response = await fetch("/api/transactions", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: new URLSearchParams(transaction)
        });

        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.error || "Unable to add transaction.");
        }

        form.reset();

        showMessage("Transaction added successfully!", false);

        await loadTransactions();

    } catch (error) {
        showMessage(error.message, true);
    } finally {
        submitButton.disabled = false;
        submitButton.innerHTML = "<span>＋</span> Add Transaction";
    }
});

function showMessage(text, isError) {
    message.textContent = text;
    message.style.color = isError ? "#e82c4e" : "#07865e";
}

/* Monthly income vs expenses chart */

function renderMonthlyChart() {
    const svg = document.getElementById("monthly-chart");
    const width = 600;
    const height = 235;
    const left = 55;
    const right = 15;
    const top = 15;
    const bottom = 35;

    const plotWidth = width - left - right;
    const plotHeight = height - top - bottom;

    const now = new Date();

    const months = [];

    for (let offset = 3; offset >= 0; offset--) {
        months.push(new Date(
            now.getFullYear(),
            now.getMonth() - offset,
            1
        ));
    }

    const data = months.map(month => {
        const matching = transactions.filter(t => {
            if (!t.date) return false;

            const parts = t.date.split("-").map(Number);

            return parts[0] === month.getFullYear() &&
                   parts[1] === month.getMonth() + 1;
        });

        return {
            label: month.toLocaleDateString("en-US", {
                month: "short"
            }),
            income: matching
                .filter(t => t.type === "INCOME")
                .reduce((sum, t) => sum + Number(t.amount), 0),
            expenses: matching
                .filter(t => t.type === "EXPENSE")
                .reduce((sum, t) => sum + Number(t.amount), 0)
        };
    });

    const maxValue = Math.max(
        100,
        ...data.flatMap(d => [d.income, d.expenses])
    );

    const niceMax = Math.ceil(maxValue / 4 / 100) * 100 || 100;
    const scaleMax = niceMax * 4;

    let content = "";

    for (let i = 0; i <= 4; i++) {
        const value = scaleMax * (4 - i) / 4;
        const y = top + (plotHeight * i / 4);

        content += `
            <line class="grid-line"
                  x1="${left}" y1="${y}"
                  x2="${width - right}" y2="${y}"></line>
            <text x="${left - 8}" y="${y + 4}"
                  text-anchor="end">${compactMoney(value)}</text>
        `;
    }

    content += `
        <line class="axis-line"
              x1="${left}" y1="${top}"
              x2="${left}" y2="${top + plotHeight}"></line>
        <line class="axis-line"
              x1="${left}" y1="${top + plotHeight}"
              x2="${width - right}" y2="${top + plotHeight}"></line>
    `;

    const groupWidth = plotWidth / 4;
    const barWidth = Math.min(19, groupWidth * 0.22);

    data.forEach((d, i) => {
        const center = left + groupWidth * (i + 0.5);

        const incomeHeight = d.income / scaleMax * plotHeight;
        const expenseHeight = d.expenses / scaleMax * plotHeight;

        content += `
            <rect x="${center - barWidth - 2}"
                  y="${top + plotHeight - incomeHeight}"
                  width="${barWidth}" height="${incomeHeight}"
                  rx="3" fill="#16a875">
                <title>${d.label} income: ${money(d.income)}</title>
            </rect>

            <rect x="${center + 2}"
                  y="${top + plotHeight - expenseHeight}"
                  width="${barWidth}" height="${expenseHeight}"
                  rx="3" fill="#fa4b68">
                <title>${d.label} expenses: ${money(d.expenses)}</title>
            </rect>

            <text x="${center}" y="${height - 9}"
                  text-anchor="middle">${d.label}</text>
        `;
    });

    svg.innerHTML = content;
}

function compactMoney(value) {
    if (value >= 100000) {
        return "₹" + (value / 100000).toFixed(1) + "L";
    }

    if (value >= 1000) {
        return "₹" + (value / 1000).toFixed(1) + "k";
    }

    return "₹" + Math.round(value);
}

/* Expense breakdown donut chart */

function renderDonutChart() {
    const donut = document.getElementById("expense-donut");
    const legend = document.getElementById("category-legend");

    const totals = {};

    transactions
        .filter(t => t.type === "EXPENSE")
        .forEach(t => {
            const category = t.category || "Other";

            totals[category] =
                (totals[category] || 0) + Number(t.amount);
        });

    const entries = Object.entries(totals)
        .sort((a, b) => b[1] - a[1]);

    const total = entries.reduce((sum, item) => sum + item[1], 0);

    legend.innerHTML = "";

    if (total === 0) {
        donut.style.background =
            "conic-gradient(#e7edf7 0% 100%)";

        legend.textContent = "No expenses recorded yet.";
        legend.style.color = "#7588a8";
        legend.style.fontSize = "11px";
        return;
    }

    let current = 0;
    const segments = [];

    entries.forEach(([category, amount], index) => {
        const percent = amount / total * 100;
        const next = current + percent;
        const color = chartColors[index % chartColors.length];

        segments.push(`${color} ${current}% ${next}%`);
        current = next;

        const item = document.createElement("div");
        item.className = "category-item";

        const dot = document.createElement("span");
        dot.className = "category-color";
        dot.style.background = color;

        const name = document.createElement("span");
        name.className = "category-name";
        name.textContent = category;

        const percentage = document.createElement("span");
        percentage.className = "category-percent";
        percentage.textContent = Math.round(percent) + "%";

        item.append(dot, name, percentage);
        legend.appendChild(item);
    });

    donut.style.background =
        `conic-gradient(${segments.join(", ")})`;
}

function renderCharts() {
    renderMonthlyChart();
    renderDonutChart();
}

/* Financial report */

document.getElementById("generate-report")
    .addEventListener("click", async () => {
        const output = document.getElementById("report-output");
        output.textContent = "Generating report...";

        try {
            const response = await fetch("/api/report", {
                method: "POST"
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.error || "Could not generate report.");
            }

            output.textContent = result.report;

        } catch (error) {
            output.textContent = error.message;
        }
    });

/* Initial page load */

loadTransactions();