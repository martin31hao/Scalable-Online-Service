
public class ServerRole implements java.io.Serializable{
	/**
	 * A class that contains information of server type, frontier or app server
	 * and uid incremental from 1000
	 */
	private static final long serialVersionUID = 5784750911169383259L;
	public int uid;
	public int type;
	
	public ServerRole(int uid, int type) {
		this.uid = uid;
		this.type = type;
	}
}
