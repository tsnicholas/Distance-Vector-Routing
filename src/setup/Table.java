package setup;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


// This class represents the distance vector that a router initiates, optimizes, and exchanges with neighbors
public class Table implements Serializable {
    private final HashMap<Integer, Integer> directLinks = new HashMap<>();
    private final HashMap<Integer, RouteRecord> entries = new HashMap<>();
    private int id;

    public void addId(int id) {
        this.id = id;
    }

    public void addEntry(int id, RouteRecord record) {
        entries.put(id, record);
        directLinks.put(id, record.distance);
    }

    public HashMap<Integer, RouteRecord> getEntries() {
        return entries;
    }

    public int getDirectLink(int id) {
        return directLinks.get(id);
    }

    public int getId() {
        return id;
    }

    public void updateEntry(int id, RouteRecord route) {
        entries.replace(id, route);
    }

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder();
        for(Map.Entry<Integer, RouteRecord> entry : entries.entrySet()) {
            output.append(entry.getKey()).append(" --> ").append(entry.getValue().toString()).append("\n");
        }
        return output.toString();
    }
}
