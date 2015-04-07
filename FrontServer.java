import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/*
 * FrontServer:
 * Front server does the following things:
 * 		1. Send P/B request to PM or BM
 * 		2. Receive kill signal from FM, and be ready to kill after all requests have been sent 
 * 	
 */
public class FrontServer extends UnicastRemoteObject implements FrontServerIf {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5435828226261085525L;

	private ServerLib SL;
	
	//private int port = 0;
	
	private BMasterIf BM = null;
	private BMasterIf PM = null;
	
	private boolean toKill = false;
	
	// BMaster which should be sent request to
	public FrontServer(ServerLib SL, String addr, int port) throws RemoteException {
		this.SL = SL;
		//this.port = port;
		BM = getBMasterInstance(addr, port, Server.BROWSESERVER);
		PM = getBMasterInstance(addr, port, Server.PURCHASESERVER);
	}
	
	/*
	 * Get request from its queue and keep sending to corresponding app server
	 */
	public void run() {
		// register with load balancer so requests are sent to this server
        SL.register_frontend();
        
        while (toKill == false) {
        	Cloud.FrontEndOps.Request nowReq = SL.getNextRequest();
        	
        	if (nowReq == null) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				continue;
			}
        	
        	if (nowReq.isPurchase) {
        		try {
        			RequestPacket rp = new RequestPacket(1, nowReq);
    				PM._sendRequest(rp);
    			} catch (RemoteException e1) {
    				e1.printStackTrace();
    			}
        	} else {
        		try {
        			RequestPacket rp = new RequestPacket(1, nowReq);
    				BM._sendRequest(rp);
    			} catch (RemoteException e1) {
    				e1.printStackTrace();
    			}       		
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        SL.shutDown();
	}
	
	/*
	 * RMI call for Front Master to kill it
	 * 1. unregister from cloud first
	 * 2. send remaining request from queue
	 * 3. kill itself
	 */
	@Override
	public void _kill() throws RemoteException{
		// Get unregistered from Cloud first
		SL.unregister_frontend();
	
	 	toKill = true;
	}
	
	// Get front master instance
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
	
	// Get B Master remote instance
	public BMasterIf getBMasterInstance(String addr, int port, int serverType) {
		String url = null;
		if (serverType == Server.BROWSESERVER)
			url = String.format("//%s:%d/BMService", addr, port);
		else
			url = String.format("//%s:%d/PMService", addr, port);
	    try {
	      return (BMasterIf) Naming.lookup(url);
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

	@Override
	public void _dropReq(Cloud.FrontEndOps.Request r) throws RemoteException {
		// TODO Auto-generated method stub
		System.out.println("Drop a request from server");
		SL.drop(r);
	}
}
