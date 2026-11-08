import java.time.LocalDate;

public abstract class Transaction {
    protected int id;
    protected double amount;
    protected String description;
    protected LocalDate date;

    public Transaction(int id, double amount, String description) {
        this.id = id;
        this.amount = amount;
        this.description = description;
        this.date = LocalDate.now();
    }

    public int getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getDate() {
        return date;
    }

    public abstract String getType();

    public abstract void displayDetails();
}