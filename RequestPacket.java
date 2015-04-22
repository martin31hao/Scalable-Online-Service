
public class RequestPacket implements java.io.Serializable {
	/**
	 * A class that contains information of original requests
	 * and the time when the request is issued from load balancer
	 */
	private static final long serialVersionUID = 4591438252882395278L;
	public Cloud.FrontEndOps.Request r;
	public long getTime;
	
	public RequestPacket(Cloud.FrontEndOps.Request req) {
		r = req;
		getTime = System.currentTimeMillis();
	}
}
