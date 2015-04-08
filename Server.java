import java.net.MalformedURLException;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/* Sample code for basic Server */

public class Server {
	public static final int FRONTSERVER = 0;
	public static final int BROWSESERVER = 1;
	public static final int PURCHASESERVER = 2;
    // Number of max waiting client to drop client from queue
    //private final int maxWaitingClient = 10;
	private static String cAddr = null;
     
    public static void main ( String args[] ) throws Exception {
        if (args.length != 2) throw new Exception("Need 2 args: <cloud_ip> <cloud_port>");
        ServerLib SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
        cAddr = args[0];
        int port = Integer.parseInt(args[1]);
        
        if (isPrimaryServer(SL, args[0], port, "FrontMaster")) {
            System.out.println("Is Front master");
    				
            FrontMaster fm = null;
    		fm = new FrontMaster(SL, cAddr, port);
    			
    		try {
    			Naming.rebind(String.format("//%s:%d/ServerService", cAddr, port), fm);
    			System.out.println("Bind Front Master successful!");
    		} catch (RemoteException e) {
    			System.err.println(e); //you probably want to do some decent logging here
    		} catch (MalformedURLException e) {
    			System.err.println(e); //you probably want to do some decent logging here
    		}
    		
            SL.startVM(); // start BM
            Thread.sleep(1500);
            SL.startVM(); // start PM
    		
            fm.run();
        } else if (isPrimaryServer(SL, args[0], port, "BMaster")) {
        	System.out.println("Is B master");
    		
        	BMaster fm = null;
        	
    		fm = new BMaster(SL, cAddr, port, BROWSESERVER);
    			
    		try {
    			Naming.rebind(String.format("//%s:%d/BMService", cAddr, port), fm);
    		} catch (RemoteException e) {
    			System.err.println(e); //you probably want to do some decent logging here
    		} catch (MalformedURLException e) {
    			System.err.println(e); //you probably want to do some decent logging here
    		}
        
        	fm.run();
        } else if (isPrimaryServer(SL, args[0], port, "PMaster")) {
            System.out.println("Is P master");
            BMaster fm = new BMaster(SL, cAddr, port, PURCHASESERVER);
            
            try {
    			Naming.rebind(String.format("//%s:%d/PMService", cAddr, port), fm);
    		} catch (RemoteException e) {
    			System.err.println(e); //you probably want to do some decent logging here
    		} catch (MalformedURLException e) {
    			System.err.println(e); //you probably want to do some decent logging here
    		}
            
            fm.run();
        } else {
        	// ask for role
        	FrontMasterIf master = getServerInstance(cAddr, port);
        	ServerRole role = null;
        	while (role == null) {
	        	try {
	        		role = master._getRoleUID();
	        	} catch (RemoteException e) {
	        		Thread.sleep(200);
	        	}
        	}
        	if (role.type == FRONTSERVER) { // This means a front server
        		System.out.println("Is Front Server with uid " + role.uid);
        		FrontServer fs = new FrontServer(SL, cAddr, port);
        		// register itself to registry
        		try {
        			Naming.rebind(String.format("//%s:%d/%d", cAddr, port, role.uid), fs);
        		} catch (RemoteException e) {
        			System.err.println(e); //you probably want to do some decent logging here
        		} catch (MalformedURLException e) {
        			System.err.println(e); //you probably want to do some decent logging here
        		}
        		fs.run();
        	} else if (role.type == PURCHASESERVER) { // This means a purchase server
        		System.out.println("Is Purchase Server with uid " + role.uid);
        		BServer bs = new BServer(SL, cAddr, port, role.uid, role.type);
        		// register itself to registry
        		try {
        			Naming.rebind(String.format("//%s:%d/%d", cAddr, port, role.uid), bs);
        		} catch (RemoteException e) {
        			System.err.println(e); //you probably want to do some decent logging here
        		} catch (MalformedURLException e) {
        			System.err.println(e); //you probably want to do some decent logging here
        		}
        		bs.run();
        	} else { // This means a browse server
        		System.out.println("Is Browse Server with uid " + role.uid);
        		BServer bs = new BServer(SL, cAddr, port, role.uid, role.type);
        		// register itself to registry
        		try {
        			Naming.rebind(String.format("//%s:%d/%d", cAddr, port, role.uid), bs);
        		} catch (RemoteException e) {
        			System.err.println(e); //you probably want to do some decent logging here
        		} catch (MalformedURLException e) {
        			System.err.println(e); //you probably want to do some decent logging here
        		}
        		bs.run();
        	}
        }
        
    }
    
    /*
     * Check whether the server is the primary server
     * If so, it can assign new server and delete over-assigned server
     */
    public static boolean isPrimaryServer(ServerLib sl,
                                          String addr, int port, String name) {
        Registry reg = null;
        try {
            reg = LocateRegistry.getRegistry(addr, port);
        } catch (RemoteException e1) {
            e1.printStackTrace();
        }
        
        try {
            reg.bind(name, reg.lookup("Cloud"));
            return true;
        } catch (AccessException e) {
            e.printStackTrace();
            return false;
        } catch (NotBoundException e) {
            e.printStackTrace();
            return false;
        } catch (AlreadyBoundException e) {
        	//e.printStackTrace();
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
    
    
    public static FrontMasterIf getServerInstance(String addr, int port) {
	    String url = String.format("//%s:%d/ServerService", addr, port);
	    try {
	      return (FrontMasterIf) Naming.lookup(url);
	    } catch (MalformedURLException e) {
	      //you probably want to do logging more properly
	      System.err.println("Bad URL" + e);
	    } catch (RemoteException e) {
	      System.err.println("Remote connection refused to url "+ url + " " + e);
	    } catch (NotBoundException e) {
	      System.err.println("Not bound " + e);
	    }
	    return null;
  	}
}

