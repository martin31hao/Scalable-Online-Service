import java.rmi.*;

public interface FrontMasterIf extends Remote {
	// PM and BM call add server to let Front master know role of incoming new VM
	public void _addServer(int type) throws RemoteException;
	// new VM call this func to get role and uid
	public ServerRole _getRoleUID() throws RemoteException;
	// for BMaster to drop packet if its queue is too full
	public void _dropReq(Cloud.FrontEndOps.Request r) throws RemoteException;
}
