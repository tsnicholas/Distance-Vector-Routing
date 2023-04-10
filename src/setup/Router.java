package setup;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Router {
    private final int _Id;
    private final int _port;
    private final JSONTool _jsonTool;

    // maximum size of a UDP packet allowed
    private final int COMM_BYTE_SIZE = 1048;

    // the router's own distance vector
    private final Table _table = new Table();

    // a list of all neighbors
    private final List<Integer> _neighborIds = new ArrayList<>();

    private final DatagramSocket _datagramSocket;


    /*
       The constructor starts the router:
          - it initializes the distance vector;
	  - it populates the list of neighbors, and
          - it sends out the initial distance vector to all neighbors;
       The constructor also starts a thread to periodically send out its distance vector to all neighbors (keep alive)
     */
    public Router(int routerNumber) throws Exception {
        _Id = routerNumber;
        try {
            _jsonTool = new JSONTool(routerNumber);
        } catch (IOException e) {
            throw new Error(e.getMessage());
        }
        // Get and initialize neighbor links
        List<Link> links = _jsonTool.getLinks();
        initializeTable(links);
        initializeNeighbors(links);

        // Get self port
        _port = _jsonTool.getPort();

        // Create a DataGramSocket to listen for communication
        _datagramSocket = new DatagramSocket(_port);
        sendTablePeriodically(5, 10);
    }
    
    // This method keeps the router running by executing an infinite loop
    @SuppressWarnings("InfiniteLoopStatement")
    public void runRouter() throws Exception {
        while (true) {
            DatagramPacket datagramPacket = new DatagramPacket(new byte[COMM_BYTE_SIZE], COMM_BYTE_SIZE);
            _datagramSocket.receive(datagramPacket);
            Table table = receiveTable(datagramPacket);
            if(optimizeTable(table)) {
                sendTable(_jsonTool.getIP(), _jsonTool.getPortById(table.getId()), splitHorizon(table.getId()));
            }
        }
    }

    /* Private methods */

    private Table splitHorizon(int destinationRouterId) {
        Table output = new Table();
        HashMap<Integer, RouteRecord> entries = _table.getEntries();
        for(Map.Entry<Integer, RouteRecord> entry : entries.entrySet()) {
            if(!(entry.getValue().nextHop == destinationRouterId)) {
                output.addEntry(entry.getKey(), entry.getValue());
            } else {
                output.addEntry(entry.getKey(), new RouteRecord(Integer.MAX_VALUE, destinationRouterId));
            }
        }
        return output;
    }

    // This method is called whenever a distance vector is received from a neighbor.
    private boolean optimizeTable(Table incomingTable) {
        boolean isOptimized = false;
        HashMap<Integer, RouteRecord> current = _table.getEntries();
        HashMap<Integer, RouteRecord> incoming = incomingTable.getEntries();
        for(Map.Entry<Integer, RouteRecord> route : incoming.entrySet()) {
            int neighborId = incomingTable.getId();
            int derivedDistance = _table.getDirectLink(neighborId) + incoming.get(route.getKey()).distance;
            if(current.containsKey(route.getKey())) {
                if (derivedDistance < current.get(route.getKey()).distance) {
                    _table.updateEntry(route.getKey(), new RouteRecord(derivedDistance, neighborId));
                    isOptimized = true;
                }
            } else {
                _table.addEntry(route.getKey(), new RouteRecord(derivedDistance, neighborId));
            }
        }
        System.out.println("Optimized Table: ");
        System.out.println(_table);
        return isOptimized;
    }

    private void initializeTable(List<Link> links) {
        _table.addId(_Id);
        for(Link link : links) {
            int otherRouterId = link.connectingRouterId().get(0) == _Id ?
                    link.connectingRouterId().get(1) : link.connectingRouterId().get(0);
            _table.addEntry(otherRouterId, new RouteRecord(link.weight(), otherRouterId));
        }
        _table.addEntry(_Id, new RouteRecord(0, _Id));
        System.out.println("Initial direct link table");
        System.out.println(_table);
    }

    private void initializeNeighbors(List<Link> links) {
        for(Link link : links) {
            if(link.connectingRouterId().get(0) == _Id) {
                _neighborIds.add(link.connectingRouterId().get(1));
            } else {
                _neighborIds.add(link.connectingRouterId().get(0));
            }
        }
    }

    /* BELOW METHOD SHOULD NOT NEED CHANGED */

    /**
     * Receives table from incoming DatagramPacket
     * @param dgp DatagramPacket
     * @return Table
     * @throws Exception
     */
    private Table receiveTable(DatagramPacket dgp) throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(dgp.getData(), 0, dgp.getLength());
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
        return (Table) objectInputStream.readObject();
    }

    /**
     * Sends table to specified router
     * @param IP
     * @param port
     * @param table
     * @throws Exception
     */
    private void sendTable(InetAddress IP, int port, Table table) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(COMM_BYTE_SIZE);
        ObjectOutputStream oos = new ObjectOutputStream(outputStream);
        oos.writeObject(table);
        if (outputStream.size() > COMM_BYTE_SIZE) throw new Exception("Message too large");
        _datagramSocket.send(new DatagramPacket(outputStream.toByteArray(), outputStream.size(), IP, port));
    }

    /**
     * Sends the _table member variable every {interval} seconds
     * @param delay
     * @param interval
     */
    private void sendTablePeriodically(int delay, int interval) {
        Runnable helloRunnable = () -> {
            for (int neighborId : _neighborIds) {
                try {
                    InetAddress otherAddress = _jsonTool.getIPById(neighborId);
                    int otherPort = _jsonTool.getPortById(neighborId);
                    sendTable(otherAddress, otherPort, _table);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(helloRunnable, delay, interval, TimeUnit.SECONDS);
    }
}
