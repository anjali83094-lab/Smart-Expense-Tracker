public class Expense extends Transaction {
    private String category;

    public Expense(int id, double amount, String category,
                   String description) {
        super(id, amount, description);
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String getType() {
        return "EXPENSE";
    }

    @Override
    public void displayDetails() {
        System.out.println(
            "Expense: ₹" + amount +
            ", Category: " + category +
            ", Description: " + description
        );
    }
}