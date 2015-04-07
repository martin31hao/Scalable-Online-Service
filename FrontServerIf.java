import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FrontServerIf extends Remote {
	public void _kill() throws RemoteException;
	
	public void _dropReq(Cloud.FrontEndOps.Request r) throws RemoteException;
}
