/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package TestClient;

import java.io.*;
import HTTPClient.*;

abstract class Request {

    protected URI url;
    protected byte[] desired;

    public void setUrl(String url) {
	try {
	    this.url = new URI( url );
	} catch (ParseException e) {
	    System.err.println("bad url: " + url);
	    System.exit(-1);
	}
    }

    URI getUrl() {
	return url;
    }

    public void setDesired(String fileName) {
	try {
	    File f = new File( fileName );
	    long length = f.length();
	    desired = new byte[ (int) length];
	    new FileInputStream(f).read(desired);
	} catch (Exception e) {
	    System.err.println("Error reading requests: " + e.getMessage());
	    System.exit( -1 );
	}
    }

    byte[] getDesired() {
	return desired;
    }

    byte[] getActual(HTTPConnection server) throws BadRequest {
	try {
	    HTTPResponse rsp = doGet( server );
	    if (rsp.getStatusCode() >= 300)
		throw new BadRequest( rsp.getReasonLine(), rsp.getText() );
	    else
		return rsp.getData();
	} catch (Throwable e) {
	    throw new BadRequest( e.getMessage(), null );
	}
    }

    abstract HTTPResponse doGet(HTTPConnection server) 
	throws IOException, ModuleException;

}

