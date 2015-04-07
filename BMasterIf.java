import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BMasterIf extends Remote {
	// for FrontMaster to send request to BMaster's queue
	public void _sendRequest(RequestPacket rp) throws RemoteException;
	
	// for BServer to get request from its request queue
	public Cloud.FrontEndOps.Request _getRequest() throws RemoteException;
	
	// get BServer id and registry name
	public void _getBServer(String regName, String addr, int port) throws RemoteException;
}
