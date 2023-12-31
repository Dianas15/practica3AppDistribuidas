Authentication Example
==============================================

This example illustrates a simple OAuth2-based authentication implementation in gRPC using
 server interceptor. It uses the Google OAuth2 library since it already has the OAuth2
semantics which makes it easy to illustrate the OAuth2 flow. The example creates an OAuth2
credentials using the library and converts it to gRPC CallCredentials. However, you may
use your own OAuth2 implementation, so use of Google OAuth2 library is not necessary.

The example requires grpc-java to be pre-built. Using a release tag will download the relevant binaries
from a maven repository. But if you need the latest SNAPSHOT binaries you will need to follow
[COMPILING](../../COMPILING.md) to build these.

The source code is [here](src/main/java/io/grpc/examples/oauth). 
To build the example, run in this directory:
```
$ ../gradlew installDist
```
The build creates scripts `auth-server` and `auth-client` in the `build/install/example-oauth/bin/` directory
which can be used to run this example. The example requires the server to be running before starting the
client.

Running auth-server is similar to the normal hello world example and there are no arguments to supply:

**auth-server**:

The auth-server accepts optional argument for port on which the server should run:

```text
USAGE: auth-server [port]
```

The auth-client accepts optional arguments for server-host, server-port, user-name and client-id:

**auth-client**:

```text
USAGE: auth-client [server-host [server-port [user-name [client-id]]]]
```

The `user-name` value is simply passed in the `HelloRequest` message as payload and the value of
`client-id` is included in the OAuth2 access token passed in the metadata header.


#### How to run the example:

```bash
# Run the server:
./build/install/example-oauth/bin/auth-server 50051
# In another terminal run the client
./build/install/example-oauth/bin/auth-client localhost 50051 userA clientB
```

That's it! The client will show the user-name reflected back in the message from the server as follows:
```
INFO: Greeting: Hello, userA
```

And on the server side you will see the message with the client's identifier:
```
Processing request from clientB
```

## Maven

If you prefer to use Maven follow these [steps](../README.md#maven). You can run the example as follows:

```
$ # Run the server
$ mvn exec:java -Dexec.mainClass=io.grpc.examples.oauth.AuthServer -Dexec.args="50051"
$ # In another terminal run the client
$ mvn exec:java -Dexec.mainClass=io.grpc.examples.oauth.AuthClient -Dexec.args="localhost 50051 userA clientB"
```
