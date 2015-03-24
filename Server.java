/* Sample code for basic Server */

public class Server {
    public static void main ( String args[] ) throws Exception {
        if (args.length != 2) throw new Exception("Need 2 args: <cloud_ip> <cloud_port>");
        ServerLib SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
        
        // register with load balancer so requests are sent to this server
        SL.register_frontend();
        
        // main loop
        while (true) {
            Cloud.FrontEndOps.Request r = SL.getNextRequest();
            SL.processRequest( r );
        }
    }
}

