all: Server.class BServer.class BServerIf.class FrontMaster.class FrontMasterIf.class FrontServer.class FrontServerIf.class ServerRole.class RequestPacket.class

%.class: %.java
	javac $<

clean:
	rm -f *.class
