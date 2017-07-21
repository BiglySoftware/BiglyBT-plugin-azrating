/*
 * Created on 4 mars 2005
 * Created by Olivier Chalouhi
 * 
 * Copyright (C) 2004 Aelitis SARL, All rights Reserved
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * AELITIS, SARL au capital de 30,000 euros,
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */
package com.aelitis.azureus.plugins.rating.updater;

import com.biglybt.pif.download.Download;

public class KeyGenUtils {
  
  private static final byte[] ratingPostFix = ".rating".getBytes();  
  
  public static byte[] buildRatingKey(Download download) {
    return buildExtendedKey(download,ratingPostFix);
  }
  
  public static byte[] buildRatingKey(byte[] hash) {
	    return buildExtendedKeyFromHash(hash,ratingPostFix);
	  }
   
  private static byte[] buildExtendedKey(Download download,byte[] extension) {
    return buildExtendedKeyFromHash(download.getTorrent().getHash(),extension);
  }
  
  private static byte[] buildExtendedKeyFromHash(byte[] hash,byte[] extension) {
    byte[] key = new byte[20 + extension.length];
    System.arraycopy(hash,0,key,0,20);
    System.arraycopy(extension,0,key,20,extension.length);
    return key;
  }
}
