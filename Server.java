import java.util.HashSet;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/* Sample code for basic Server */

public class Server {
	// ID of servers that are active 
	private static HashSet<Integer> serverPool = null;
	
	// Number of active servers now available
	private static int activeServers = 1;
	
	// Number of max waiting client to drop client from queue
	private final int maxWaitingClient = 10;

	// Whether it is the master server
	private static boolean isMaster = false;
	
    public static void main ( String args[] ) throws Exception {
        if (args.length != 2) throw new Exception("Need 2 args: <cloud_ip> <cloud_port>");
        ServerLib SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
        int port = Integer.parseInt(args[1]);
        
        if (isPrimaryServer(SL, args[0], port)) {
			isMaster = true;
	       	serverPool = new HashSet<Integer>();
	        staticAssignVM(SL);
		}
        
        // register with load balancer so requests are sent to this server
        SL.register_frontend();
        
        // main loop
        while (true) {
            Cloud.FrontEndOps.Request r = SL.getNextRequest();
            SL.processRequest( r );

            System.out.println("Get a request with active server " + activeServers);
            
        }
    }
    
    public static void staticAssignVM(ServerLib sl) {
    	float hour = sl.getTime();
    	if (hour >= 0.0 && hour <= 1.0) { // maybe 2
    		while (activeServers < 8)
    			startNewServer(sl);
    	} else if (hour > 1.0 && hour <= 6.0) { // maybe only one is enough
    		while (activeServers < 3) {
    			startNewServer(sl);
			System.out.println("Start a new VM with " + activeServers);
    		}
    	} else if (hour > 6.0 && hour <= 7.0) { // may be 2
    		while (activeServers < 12)
    			startNewServer(sl);
    	} else if (hour > 7.0 && hour <= 8.0) { // maybe 4 or more 
    		while (activeServers < 8) {
    			startNewServer(sl);
    		}
    	} else if (hour > 8.0 && hour <= 18.0) { // maybe 6 or more
    		while (activeServers < 6) {
    			startNewServer(sl);
    		}
    	} else if (hour > 18.0 && hour <= 20) { // scale up
    		while (activeServers < 15) {
    			startNewServer(sl);
    		}
    	} else if (hour > 20.0 && hour < 22.0) { // scale down to 8 - 18
    		while (activeServers > 6) {
    			shutdownServer(sl);
    		}
    	} else { // scale down to 7 - 8
    		while (activeServers > 4) {
    			shutdownServer(sl);
    		}
    	}
    }
    
	/*
	 * Check whether the server is the primary server
	 * If so, it can assign new server and delete over-assigned server
	 */ 
	public static boolean isPrimaryServer(ServerLib sl,
		String addr, int port) {
		Registry reg = null;
		try {
			reg = LocateRegistry.getRegistry(addr, port);
		} catch (RemoteException e1) {
			e1.printStackTrace();
		}
    		
    		try {
			reg.bind("Server2", reg.lookup("Cloud"));
    			return true;
    		} catch (AccessException e) {
			e.printStackTrace();
    			return false;
    		} catch (NotBoundException e) {
			e.printStackTrace();
    			return false;
    		} catch (AlreadyBoundException e) {
			e.printStackTrace();
    			return false;
    		} catch (RemoteException e) {
			e.printStackTrace();
    			return false;
    		} catch (NullPointerException e) {
			e.printStackTrace();
    			return false;
    		} catch (Exception e) {
			e.printStackTrace();
    			return false;
		}
	}

    // Server will encounter boot time, so it not a good idea to assume it is running 
    public static synchronized void startNewServer(ServerLib sl) {
    	int serverId = sl.startVM();
    	serverPool.add(serverId);
    	System.out.println("Server " + serverId + " added.");
    	activeServers++;
    }
    
    public static synchronized void shutdownServer(ServerLib sl) {
    	for (Integer server: serverPool) {
    		// shutdown VMs that is running
    		System.out.println("Server " + server + " state: " + sl.getStatusVM(server).name());
    		if (sl.getStatusVM(server).name().equalsIgnoreCase(Cloud.CloudOps.VMStatus.Running.name())) {
    			//TODO: Don't know whether the server is on any work
    			sl.endVM(server);
    			activeServers--;
    			serverPool.remove(server);
    		}
    	}
    }
    
    public int getActiveServers (ServerLib sl) {
    	int cnt = 0;
    	for (Integer i : serverPool) {
    		if (sl.getStatusVM(i).compareTo(Cloud.CloudOps.VMStatus.Running) == 0) {
    			cnt++;
    		}
    	}
    	return cnt;
    }
}

