public class Income extends Transaction {
    private String category;

    public Income(int id, double amount, String category,
                  String description) {
        super(id, amount, description);
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String getType() {
        return "INCOME";
    }

    @Override
    public void displayDetails() {
        System.out.println(
            "Income: ₹" + amount +
            ", Category: " + category +
            ", Description: " + description
        );
    }
}