import java.io.IOException;
import java.util.List;

public interface Storable {
    void saveData(List<Transaction> transactions) throws IOException;

    List<Transaction> loadData() throws IOException;
}