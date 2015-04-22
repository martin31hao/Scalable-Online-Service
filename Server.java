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
	public static final int APPSERVER = 1;
	private static String cAddr = null;
	
	// heuristic number to start app server when front master starts up 
	private static int appServer[] = {
		1, 1, 1, 1, 1, 1, 1, 1,
		1, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 4, 2, 5, 3, 1, 1
	};
	
	// heuristic number to start frontier server when front master starts up
	private static int frontServer[] = {
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 1, 0, 1, 1, 0, 0,
	};
     
    public static void main ( String args[] ) throws Exception {
        if (args.length != 2) throw new Exception("Need 2 args:"
        		+ " <cloud_ip> <cloud_port>");
        ServerLib SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
        cAddr = args[0];
        int port = Integer.parseInt(args[1]);
        
        if (isPrimaryServer(SL, args[0], port, "FrontMaster")) {
            System.out.println("Is Front master");
    				
            FrontMaster fm = null;
    		fm = new FrontMaster(SL, cAddr, port);
    			
    		try {
    			Naming.rebind(String.format("//%s:%d/ServerService",
    					cAddr, port), fm);
    			System.out.println("Bind Front Master successful!");
    		} catch (RemoteException e) {
    			System.err.println(e); 
    		} catch (MalformedURLException e) {
    			System.err.println(e); 
    		}
    		
    		// Bind cache service in Front master
    		Cache cache = new Cache(SL.getDB());
			
			try {
				Naming.rebind(String.format("//%s:%d/CacheService",
						cAddr, port), cache);
			} catch (RemoteException e) {
				System.err.println(e); 
			} catch (MalformedURLException e) {
				System.err.println(e); 
			}
    		
			int hour = (int)SL.getTime();
            for (int i = 0; i < appServer[hour]; i++) {
            	SL.startVM(); // start another BServer
                try {
    				fm._addServer(APPSERVER);
    			} catch (RemoteException e) {
    				e.printStackTrace();
    			}
            }
            
            for (int i = 0; i < frontServer[hour]; i++) {
	            SL.startVM(); // start another BServer
	            try {
					fm._addServer(FRONTSERVER);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
            }
            fm.run();
        } else {
        	// ask for role
        	FrontMasterIf master = getServerInstance(cAddr, port);
        	ServerRole role = null;
        	while (role == null) {
	        	try {
	        		// Get its role as well as uid from front master
	        		role = master._getRoleUID();
	        	} catch (RemoteException e) {
	        		Thread.sleep(50);
	        	}
        	}
        	if (role.type == FRONTSERVER) { // This means a front server
        		System.out.println("Is Front Server with uid " + role.uid);
        		FrontServer fs = new FrontServer(SL, cAddr, port, role.uid);
        		// register itself to registry
        		try {
        			Naming.rebind(String.format("//%s:%d/%d", 
        					cAddr, port, role.uid), fs);
        		} catch (RemoteException e) {
        			System.err.println(e); 
        		} catch (MalformedURLException e) {
        			System.err.println(e); 
        		}
        		fs.run();
        	} else { // This means an app server
        		System.out.println("Is App Server with uid " + role.uid);
        		BServer bs = new BServer(SL, cAddr, port, role.uid, role.type);
        		// register itself to registry
        		try {
        			Naming.rebind(String.format("//%s:%d/%d", 
        					cAddr, port, role.uid), bs);
        		} catch (RemoteException e) {
        			System.err.println(e); 
        		} catch (MalformedURLException e) {
        			System.err.println(e); 
        		}
        		bs.run();
        	}
        }
        
    }
    
    /*
     * Check whether the server is the front master server
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
    
    /*
     *  Get front master instance
     */
    public static FrontMasterIf getServerInstance(String addr, int port) {
	    String url = String.format("//%s:%d/ServerService", addr, port);
	    try {
	      return (FrontMasterIf) Naming.lookup(url);
	    } catch (MalformedURLException e) {
	      //you probably want to do logging more properly
	      System.err.println("Bad URL" + e);
	    } catch (RemoteException e) {
	      System.err.println("Remote connection refused to url "+ 
	    		  url + " " + e);
	    } catch (NotBoundException e) {
	      System.err.println("Not bound " + e);
	    }
	    return null;
  	}
}

