import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/*
 * BServer:
 * Browse Server does the following things:
 * 		1. Pull request from BMServer, async pull another request when doing a request if possible
 * 		2. Receive kill signal from BMServer, clear now running request and stop
 * 	
 */
public class BServer extends UnicastRemoteObject implements BServerIf{
	/**
	 * 
	 */
	private static final long serialVersionUID = -7293142569312979890L;

	private ServerLib SL;
	
	private Cloud.FrontEndOps.Request nowReq = null;
	private Cloud.FrontEndOps.Request nextReq = null;
	
	private boolean toKill = false;
	
	// Remote Masters
	private BMasterIf BM = null;
	
	private int serverType;
	
	private String addr;
	private int port;
	private int uid;
	
	public BServer(ServerLib SL, String addr, int port, int id, int serverType)
		throws RemoteException {
		this.SL = SL;
		this.serverType = serverType;
		this.addr = addr;
		this.port = port;
		this.uid = id;
	}
	
	/*
	 * 
	 */
	public void run() {
		BM = getBMInstance(addr, port);
		String regName = Integer.toString(uid);
		System.out.println("VM " + uid + " Running");
		try {
			// Get it registered to BMaster
			BM._getBServer(regName, addr, port);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("VM " + uid + " Register success");
		while (toKill == false) {
			try {
				nowReq = BM._getRequest();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				//System.out.println("VM " + uid + " get request failed.");
				continue;
				//e.printStackTrace();
			}
			if (nowReq == null) {
				System.out.println("VM " + uid + " get null request.");
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				continue;
			}
				
			System.out.println("VM " + uid + " starting process.");
			SL.processRequest(nowReq);
		}
		System.out.println("VM " + uid + " Killed");
		try {
			UnicastRemoteObject.unexportObject(this, true);
		} catch (NoSuchObjectException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		SL.shutDown();
	}
	
	/*
	 * RMI call for BMaster to kill the server
	 */
	@Override
	public void _kill() throws RemoteException{
		toKill = true;
	}
	
	public BMasterIf getBMInstance(String addr, int port) {
		String url = null;
		if (serverType == Server.BROWSESERVER)
			url = String.format("//%s:%d/BMService", addr, port);
		else {
			url = String.format("//%s:%d/PMService", addr, port);
		}
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
}
