/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2013 Pentaho Corporation..  All rights reserved.
 */

package org.pentaho.marketplace.util.web;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpUtil {

  private static final String PROXY_HOST_PROPERTY_NAME = "http.proxyHost";
  private static final String PROXY_PORT_PROPERTY_NAME = "http.proxyPort";
  private static final String PROXY_USER_PROPERTY_NAME = "http.proxyUser";
  private static final String PROXY_PASSWORD_PROPERTY_NAME = "http.proxyPassword";

  private static Log logger = LogFactory.getLog( HttpUtil.class );

  public static HttpClient getClient() {

    int connectionTimeout = 3000;
    int pageTimeout = 7000;
    HttpConnectionManager connectionManager = new SimpleHttpConnectionManager();
    HttpConnectionManagerParams connectionParams = connectionManager.getParams();
    connectionParams.setConnectionTimeout( connectionTimeout );
    connectionParams.setSoTimeout( pageTimeout );

    HttpClient httpClient = null;
    if ( connectionManager != null ) {
      httpClient = new HttpClient( connectionManager );
    }

    try {
      HostConfiguration hostConfig = null;
      final String proxyHost = System.getProperty( PROXY_HOST_PROPERTY_NAME );
      final int proxyPort = Integer.parseInt( System.getProperty( PROXY_PORT_PROPERTY_NAME ) );
      if ( StringUtils.isNotEmpty( proxyHost ) ) {
        hostConfig = new HostConfiguration() {
          @Override
          public synchronized String getProxyHost() {
            return proxyHost;
          }

          @Override
          public synchronized int getProxyPort() {
            return proxyPort;
          }
        };
        httpClient.setHostConfiguration( hostConfig );
        String proxyUser = System.getProperty( PROXY_USER_PROPERTY_NAME );
        String proxyPassword = System.getProperty( PROXY_PASSWORD_PROPERTY_NAME );
        if ( proxyUser != null && proxyUser.trim().length() > 0 ) {
          httpClient.getState().setProxyCredentials(
            new AuthScope( proxyHost, proxyPort ),
            new UsernamePasswordCredentials( proxyUser, proxyPassword )
          );
        }
      }
    } catch ( Exception e ) {

    }

    return httpClient;

  }

  public static boolean getURLContent( final String url, final StringBuffer content ) {
    InputStream response = getURLInputStream( url );
    if ( response == null ) {
      return false;
    }

    try {
      byte[] buffer = new byte[ 2048 ];
      int size = response.read( buffer );
      while ( size > 0 ) {
        for ( int idx = 0; idx < size; idx++ ) {
          content.append( (char) buffer[ idx ] );
        }
        size = response.read( buffer );
      }
    } catch ( Exception e ) {
      // we can ignore this because the content comparison will fail
    }

    return true;
  }

  public static String getURLContent( final String uri ) {
      StringBuffer content = new StringBuffer();
      HttpUtil.getURLContent( uri, content );
      return content.toString();
  }

  public static InputStream getURLInputStream( final String url ) {
    HttpClient httpClient = HttpUtil.getClient();

    try {
      GetMethod call = new GetMethod( url );

      int status = httpClient.executeMethod( call );
      if ( status == 200 ) {
        return call.getResponseBodyAsStream();
      }
      return null;
    } catch ( Throwable e ) {
      logger.debug( "Unable to get input stream from " + url, e );
      return null;
    }
  }


  public static InputStream getURLInputStream( final URL url ) {
    return getURLInputStream( url.toString() );
  }


  public static Reader getURLReader( final String uri ) {
    InputStream inputStream = getURLInputStream( uri );
    if ( inputStream != null ) {
      return new InputStreamReader( inputStream );
    }

    return null;

  }

  //
  // The code in the next two methods is based on the code in HttpUtils.java
  // from
  // javax.servlet.http. HttpUtils is deprecated - so I updated the methods to
  // be a bit smarter
  // and use Map instead of Hashtable
  //
  public static Map parseQueryString( final String s ) {
    String[] valArray = null;
    if ( s == null ) {
      throw new IllegalArgumentException();
    }
    Map<String, String[]> rtn = new HashMap<String, String[]>();
    StringBuffer sb = new StringBuffer();
    String key;
    for ( StringTokenizer st = new StringTokenizer( s, "&" ); st.hasMoreTokens();
          rtn.put( key, valArray ) ) { //$NON-NLS-1$
      String pair = st.nextToken();
      int pos = pair.indexOf( '=' );
      if ( pos == -1 ) {
        throw new IllegalArgumentException();
      }
      key = HttpUtil.parseName( pair.substring( 0, pos ), sb );
      String val = HttpUtil.parseName( pair.substring( pos + 1, pair.length() ), sb );
      if ( rtn.containsKey( key ) ) {
        String[] oldVals = rtn.get( key );
        valArray = new String[ oldVals.length + 1 ];
        System.arraycopy( oldVals, 0, valArray, 0, oldVals.length );
        valArray[ oldVals.length ] = val;
      } else {
        valArray = new String[ 1 ];
        valArray[ 0 ] = val;
      }
    }
    return rtn;
  }

  private static String parseName( final String s, final StringBuffer sb ) {
    sb.setLength( 0 );
    char c;
    for ( int i = 0; i < s.length(); i++ ) {
      c = s.charAt( i );
      switch( c ) {
        case 43: { // '+'
          sb.append( ' ' );
          break;
        }
        case 37: { // '%'
          try {
            sb.append( (char) Integer.parseInt( s.substring( i + 1, i + 3 ), 16 ) );
            i += 2;
            break;
          } catch ( NumberFormatException numberformatexception ) {
            throw new IllegalArgumentException();
          } catch ( StringIndexOutOfBoundsException oob ) {
            String rest = s.substring( i );
            sb.append( rest );
            if ( rest.length() == 2 ) {
              i++;
            }
          }
          break;
        }
        default: {
          sb.append( c );
          break;
        }
      }
    }
    return sb.toString();
  }

}
