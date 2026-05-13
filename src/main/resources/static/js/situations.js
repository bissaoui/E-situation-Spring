(function () {
    const table = document.getElementById("situationsTable");
    if (!table) {
        return;
    }

    const tbody = table.querySelector("tbody");
    const headers = Array.from(table.querySelectorAll("thead tr:first-child th.sortable"));
    const filterInputs = Array.from(table.querySelectorAll(".filter-row input"));
    const baseRows = Array.from(tbody.querySelectorAll("tr"));

    const state = { sortCol: null, sortDir: "asc" };

    function normalizeText(text) {
        return (text || "").trim().toLowerCase();
    }

    function cellText(row, col) {
        const cell = row.cells[col];
        return cell ? cell.textContent.trim() : "";
    }

    function parseComparable(value) {
        const datePattern = /^\d{4}-\d{2}-\d{2}$/;
        const numberPattern = /^-?\d+(\.\d+)?$/;
        const compact = value.replace(/dhs/ig, "").replace(/\s/g, "").replace(/,/g, "");

        if (datePattern.test(value)) {
            return { type: "date", value: Date.parse(value) };
        }
        if (numberPattern.test(compact)) {
            return { type: "number", value: parseFloat(compact) };
        }
        return { type: "text", value: normalizeText(value) };
    }

    function updateSortIndicators() {
        headers.forEach((th, index) => {
            const icon = th.querySelector(".sort-indicator");
            if (!icon) {
                return;
            }
            if (state.sortCol === index) {
                icon.textContent = state.sortDir === "asc" ? "▲" : "▼";
            } else {
                icon.textContent = "";
            }
        });
    }

    function applyTableState() {
        const filters = filterInputs.map((input) => normalizeText(input.value));

        let rows = baseRows.filter((row) =>
            filters.every((filterValue, col) => {
                if (!filterValue) {
                    return true;
                }
                return normalizeText(cellText(row, col)).includes(filterValue);
            })
        );

        if (state.sortCol !== null) {
            rows.sort((a, b) => {
                const av = parseComparable(cellText(a, state.sortCol));
                const bv = parseComparable(cellText(b, state.sortCol));

                if (av.type === bv.type) {
                    if (av.value < bv.value) {
                        return state.sortDir === "asc" ? -1 : 1;
                    }
                    if (av.value > bv.value) {
                        return state.sortDir === "asc" ? 1 : -1;
                    }
                    return 0;
                }

                const left = normalizeText(cellText(a, state.sortCol));
                const right = normalizeText(cellText(b, state.sortCol));
                if (left < right) {
                    return state.sortDir === "asc" ? -1 : 1;
                }
                if (left > right) {
                    return state.sortDir === "asc" ? 1 : -1;
                }
                return 0;
            });
        }

        tbody.replaceChildren();
        rows.forEach((row, index) => {
            row.cells[0].textContent = String(index + 1);
            tbody.appendChild(row);
        });

        updateSortIndicators();
    }

    headers.forEach((th, index) => {
        th.addEventListener("click", () => {
            if (state.sortCol === index) {
                state.sortDir = state.sortDir === "asc" ? "desc" : "asc";
            } else {
                state.sortCol = index;
                state.sortDir = "asc";
            }
            applyTableState();
        });
    });

    filterInputs.forEach((input) => {
        input.addEventListener("input", applyTableState);
    });
})();
