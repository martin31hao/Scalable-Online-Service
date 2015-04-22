import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/*
 * FrontServer:
 * Front server does the following things:
 * 		1. Send requests to front master
 * 		2. Check if in a consecutive time there is no incoming requests, then
 * 		   send kill request to front master, then kill itself 
 * 	
 */
public class FrontServer extends UnicastRemoteObject implements FrontServerIf {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5435828226261085525L;

	private ServerLib SL;
	
	private FrontMasterIf FM = null;
	
	private boolean toKill = false;
	
	private int uid;
	
	// BMaster which should be sent request to
	public FrontServer(ServerLib SL, String addr, int port, int id) 
			throws RemoteException {
		this.SL = SL;
		FM = getFrontMasterInstance(addr, port);
		// register with load balancer so requests are sent to this server
		SL.register_frontend();
		uid = id;
	}
	
	/*
	 * Get request from its queue and keep sending to Front Master
	 */
	public void run() {
        long lastReqTime = System.currentTimeMillis();
        while (true) {
        	Cloud.FrontEndOps.Request nowReq = SL.getNextRequest();
        	
        	if (nowReq == null) {
        		/*
        		 *  If the server cannot get request in consecutive 2 sec, 
        		 *  then try to kill itself
        		 */
        		if (System.currentTimeMillis() - lastReqTime > 2000) {
					try {
						if (FM._scaleInFront(uid)) {
							SL.unregister_frontend();
							break;
						} else {
							lastReqTime = System.currentTimeMillis(); 
						}
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				continue;
			}
        	
    		try {
    			// Continue to send requests to front master
    			lastReqTime = System.currentTimeMillis();
    			FM._sendRequest(new RequestPacket(nowReq));
				
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
        }
        
        Cloud.FrontEndOps.Request nowReq = null;
        while ((nowReq = SL.getNextRequest()) != null) {
        	System.out.println("Drop request before killing");
        	SL.drop(nowReq);
        }
        System.out.println("Drop finished");
        
        try {
			UnicastRemoteObject.unexportObject(this, true);
		} catch (NoSuchObjectException e) {
			e.printStackTrace();
		}
        SL.shutDown();
	}
	
	/*
	 * Deprecated:
	 * RMI call for Front Master to kill it
	 * 1. unregister from cloud first
	 * 2. send remaining request from queue
	 * 3. kill itself
	 */
	@Override
	public void _kill() throws RemoteException{
		// Get unregistered from Cloud first
	 	toKill = true;
	}
	
	/*
	 *  Get front master instance
	 */
	public FrontMasterIf getFrontMasterInstance(String addr, int port) {
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
