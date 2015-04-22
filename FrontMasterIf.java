import java.rmi.*;

public interface FrontMasterIf extends Remote {
	/*
	 *  RMI to add server to let Front master know role of incoming new VM
	 */
	public void _addServer(int type) throws RemoteException;
	
	/*
	 *  RMI for new VM call this func to get role and uid
	 */
	public ServerRole _getRoleUID() throws RemoteException;
	
	/*
	 * RMI for app server to announce itself to front master that it is available
	 * and ready to get request from front master
	 */
	public RequestPacket _appAvailable(Integer uid) throws RemoteException;
	
	/*
	 * RMI for front end server to send request to front master
	 */
	public void _sendRequest(RequestPacket rp) throws RemoteException;
	
	/*
	 * RMI for app server to scale in itself
	 */
	public boolean _scaleInApp(int uid) throws RemoteException;
	
	/*
	 * RMI for front end server to scale in itself
	 */
	public boolean _scaleInFront(int uid) throws RemoteException;
}
