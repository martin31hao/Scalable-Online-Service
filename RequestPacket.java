
public class RequestPacket implements java.io.Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4591438252882395278L;
	public int uid;
	public Cloud.FrontEndOps.Request r;
	
	public RequestPacket(int id, Cloud.FrontEndOps.Request req) {
		uid = id;
		r = req;
	}
}
