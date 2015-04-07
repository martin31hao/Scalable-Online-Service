import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BServerIf extends Remote {
	// Get kill signal from BMaster, finishing process current process and kill itself
	public void _kill() throws RemoteException;
}
