import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class FileStorage implements Storable {

    private final Path filePath =
            Paths.get("data", "transactions.txt");

    public FileStorage() throws IOException {
        Files.createDirectories(filePath.getParent());

        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }
    }

    @Override
    public void saveData(List<Transaction> transactions)
            throws IOException {

        List<String> lines = new ArrayList<>();

        for (Transaction t : transactions) {
            String category = "";
            String type = t.getType();

            if (t instanceof Expense) {
                category = ((Expense) t).getCategory();
            } else if (t instanceof Income) {
                category = ((Income) t).getCategory();
            }

            String encodedCategory = Base64.getEncoder()
                    .encodeToString(
                        category.getBytes(StandardCharsets.UTF_8));

            String encodedDescription = Base64.getEncoder()
                    .encodeToString(
                        t.getDescription().getBytes(StandardCharsets.UTF_8));

            lines.add(
                t.getId() + "|" +
                type + "|" +
                t.getAmount() + "|" +
                encodedCategory + "|" +
                encodedDescription + "|" +
                t.getDate()
            );
        }

        Files.write(
            filePath,
            lines,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    @Override
    public List<Transaction> loadData() throws IOException {

        List<Transaction> transactions = new ArrayList<>();

        for (String line : Files.readAllLines(
                filePath, StandardCharsets.UTF_8)) {

            if (line.isBlank()) {
                continue;
            }

            String[] parts = line.split("\\|", -1);

            if (parts.length != 6) {
                continue;
            }

            try {
                int id = Integer.parseInt(parts[0]);
                String type = parts[1];
                double amount = Double.parseDouble(parts[2]);

                String category = new String(
                    Base64.getDecoder().decode(parts[3]),
                    StandardCharsets.UTF_8);

                String description = new String(
                    Base64.getDecoder().decode(parts[4]),
                    StandardCharsets.UTF_8);

                LocalDate date = LocalDate.parse(parts[5]);

                Transaction transaction;

                if (type.equals("EXPENSE")) {
                    transaction = new Expense(
                        id, amount, category, description);
                } else if (type.equals("INCOME")) {
                    transaction = new Income(
                        id, amount, category, description);
                } else {
                    continue;
                }

                // Restore the original date from the file.
                transaction.date = date;

                transactions.add(transaction);

            } catch (IllegalArgumentException e) {
                System.out.println(
                    "Skipping invalid transaction record.");
            }
        }

        return transactions;
    }
}