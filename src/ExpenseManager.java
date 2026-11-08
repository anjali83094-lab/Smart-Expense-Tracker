import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpenseManager {

    private final List<Transaction> transactions =
            new ArrayList<>();

    private final FileStorage storage;

    private int nextId = 1;

    public ExpenseManager() throws IOException {
        storage = new FileStorage();

        transactions.addAll(storage.loadData());

        for (Transaction t : transactions) {
            if (t.getId() >= nextId) {
                nextId = t.getId() + 1;
            }
        }
    }

    public synchronized Transaction addTransaction(
            String type,
            double amount,
            String category,
            String description) throws IOException {

        if (amount <= 0 || !Double.isFinite(amount)) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero.");
        }

        if (category == null || category.isBlank()
                || description == null
                || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Category and description are required.");
        }

        Transaction transaction;

        if (type.equalsIgnoreCase("EXPENSE")) {
            transaction = new Expense(
                    nextId++, amount, category, description);
        } else if (type.equalsIgnoreCase("INCOME")) {
            transaction = new Income(
                    nextId++, amount, category, description);
        } else {
            throw new IllegalArgumentException(
                    "Invalid transaction type.");
        }

        transactions.add(transaction);

        try {
            storage.saveData(transactions);
        } catch (IOException e) {
            transactions.remove(transaction);
            nextId--;
            throw e;
        }

        return transaction;
    }

    public synchronized List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    public synchronized double getTotalIncome() {
        double total = 0;

        for (Transaction t : transactions) {
            if (t instanceof Income) {
                total += t.getAmount();
            }
        }

        return total;
    }

    public synchronized double getTotalExpenses() {
        double total = 0;

        for (Transaction t : transactions) {
            if (t instanceof Expense) {
                total += t.getAmount();
            }
        }

        return total;
    }

    public synchronized double getBalance() {
        return getTotalIncome() - getTotalExpenses();
    }

    public synchronized Map<String, Double>
            getCategoryTotals() {

        Map<String, Double> totals = new HashMap<>();

        for (Transaction t : transactions) {
            if (t instanceof Expense) {
                Expense expense = (Expense) t;

                totals.put(
                    expense.getCategory(),
                    totals.getOrDefault(
                        expense.getCategory(), 0.0)
                        + expense.getAmount()
                );
            }
        }

        return totals;
    }

    public synchronized String generateReport() {
        return String.format(
            java.util.Locale.US,
            "Total Income: ₹%.2f%n" +
            "Total Expenses: ₹%.2f%n" +
            "Balance: ₹%.2f",
            getTotalIncome(),
            getTotalExpenses(),
            getBalance()
        );
    }
}