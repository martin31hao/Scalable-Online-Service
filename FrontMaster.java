import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/*
 * FrontMaster:
 * Front master does the following things:
 * 		1. Assign roles to all the incoming servers, _getRole
 * 		2. Scale out on the front servers, scaleOut
 * 		3. Send P/B request to PMaster and BMaster server, _sendRequest
 * 		4. Get start server request from P/B master, and maintain a serverQueue for role query of new VMs
 * 	
 * Note: This server is also the master server for all
 */
public class FrontMaster extends UnicastRemoteObject implements FrontMasterIf{
	/**
	 * 
	 */
	private static final long serialVersionUID = -2685316105835292212L;

	// Three constants representing 3 different types of server
	private final int FRONTSERVER = 0;
	
	private LinkedList<Integer> serverQueue; // Queue of types of servers that will be assigned roles
	private List<Integer> frontServers; // List of uid of front-end servers besides master
	
	private ServerLib SL;
	private boolean pmAlive = false;
	private boolean bmAlive = false;
	
	private static int uid = 1000; // uid for assigning to other servers
	
	private BMasterIf BM = null;
	private BMasterIf PM = null;
	
	private String addr = null;
	private int port;
	
	private final int scaleOutThreshold = 2;
	private final int scaleInThreshold = 1;
	
	public FrontMaster(ServerLib SL, String addr, int port) throws RemoteException {
		this.SL = SL;
		serverQueue = new LinkedList<Integer>();
		frontServers = new ArrayList<Integer>();
		this.addr = addr;
		this.port = port;
		
		// running checking scale out in another thread
		new Thread(new CheckScale()).start();
	}
	
	public void run() {
		// register with load balancer so requests are sent to this server
        SL.register_frontend();
        
        // main loop
        while (true) {
            Cloud.FrontEndOps.Request r = SL.getNextRequest();
            if (r.isPurchase) { // is purchase request
            	// Send request to PMaster
            	if (pmAlive == false) {
            		waitForAlive(addr, port, Server.PURCHASESERVER);
            		pmAlive = true;
            	}
            	// send requests to PM
            	try {
            		RequestPacket rp = new RequestPacket(1, r);
					PM._sendRequest(rp);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            } else { // is browse request
            	
            	// Send request to BMaster
            	if (bmAlive == false) {
            		waitForAlive(addr, port, Server.BROWSESERVER);
            		bmAlive = true;
            	}
            	// send requests to PM
            	try {
            		RequestPacket rp = new RequestPacket(1, r);
					BM._sendRequest(rp);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        }
	}
	
	/*
	 * Decide to scale out or scale in
	 */
	public void scaleOut() {
		if (toScaleOut()) {
			startFrontServer();
		}
		if (toScaleIn()) {
			shutFrontServer();
		}
	}
	
	/*
	 * Scale out when there happens that it meets requirement for two consecutive times
	 */
	public boolean toScaleOut() {
		int tryScale = 2;
		System.out.println("Front master queue size: " + SL.getQueueLength());
		while (tryScale-- > 0) {
			if (SL.getQueueLength() > scaleOutThreshold) {
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				return false;
			}
		}
		return true;
	}
	
	/*
	 * Scale in when there happens it meets requirement for three consecutive times
	 */
	public boolean toScaleIn() {
		int tryScale = 3;
		while (tryScale-- > 0) {
			if (SL.getQueueLength() < scaleInThreshold) {
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				return false;
			}
		}
		return true;
	}
	
	/*
	 * Start front server when needed
	 */
	public synchronized void startFrontServer() {
		serverQueue.add(FRONTSERVER);
		SL.startVM();
	}
	
	/*
	 * Shut down front server when needed
	 */
	public synchronized void shutFrontServer() {
		// kill the last server in the list
		int idx = frontServers.size() - 1;
		if (idx == -1)	return;
		int suid = frontServers.get(idx);
		frontServers.remove(idx);
		
		// Kill server by sending signal to it
		try {
			getFServerInstance(addr, port, suid)._kill();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		};
		
	}
	
	/*
	 * RMI call for pm and bm server to be registered to the master frontend server
	 */
	@Override
	public synchronized void _addServer(int type) throws RemoteException {
		serverQueue.add(type);
	}
	
	/*
	 * RMI call for new server to get role as well as uid of the new server
	 */
	@Override
	public synchronized ServerRole _getRoleUID() throws RemoteException {
		if (serverQueue.size() > 0) {
			if (serverQueue.peek() == FRONTSERVER) {
				frontServers.add(uid);
			}
			ServerRole role = new ServerRole(uid, serverQueue.poll());
			uid++;
			return role;
		} else {
			System.err.println("_getRole: Why is there a get role when queue is empty?");
			return null;
		}
	}
	
	/*
	 * try to get BMInstance, wait until it is alive
	 */
	public void waitForAlive(String addr, int port, int serverType) {
		if (serverType == Server.BROWSESERVER) {
			while ((BM = getBMInstance(addr, port, serverType)) == null) {
				// keep waiting for PM to get alive, inquire every 500ms
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				while (SL.getQueueLength() > 0) {
					SL.dropHead();
				}
			}
		} else {
			while ((PM = getBMInstance(addr, port, serverType)) == null) {
				// keep waiting for PM to get alive, inquire every 500ms
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				while (SL.getQueueLength() > 0) {
					SL.dropHead();
				}
			}
		}
	}
	
	public BMasterIf getBMInstance(String addr, int port, int serverType) {
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
	
	public FrontServerIf getFServerInstance(String addr, int port, int suid) {
	    String url = String.format("//%s:%d/%d", addr, port, suid);
	    try {
	      return (FrontServerIf) Naming.lookup(url);
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
	
	public class CheckScale implements Runnable{

		@Override
		public void run() {
			// TODO Auto-generated method stub
			while (true) {
				scaleOut();
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}

	/*@Override
	public void _dropReq(Cloud.FrontEndOps.Request r) throws RemoteException {
		// TODO Auto-generated method stub
		System.out.println("Drop a request from front master");
		SL.drop(r);
	}*/
}
