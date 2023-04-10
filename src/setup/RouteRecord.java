package setup;

import java.io.Serializable;


public class RouteRecord implements Serializable {
    int distance;
    int nextHop;

    public RouteRecord(int distance, int nextHop) {
        this.distance = distance;
        this.nextHop = nextHop;
    }

    @Override
    public String toString() {
        return "distance: " + distance + " next hop: " + nextHop;
    }
}
