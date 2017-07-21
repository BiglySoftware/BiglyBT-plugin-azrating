/*
 * Created on 11 mars 2005
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

import java.util.HashMap;
import java.util.Map;
import com.biglybt.pif.utils.StaticUtilities;

public class RatingData {

  private static final String STRING_ENCODING = "UTF-8";
  
  public static final int MAX_SCORE = 5;
  
  //From 0 : unrated to 5 : very good
  private int score;
  
  //Rater's nick
  private String nick;
  
  //Rater's comment
  private String comment;
  
  public RatingData(int score,String nick,String comment) {
    this.score = Math.min(MAX_SCORE, score);
    this.nick = nick;
    this.comment = comment;
  }
  
  public RatingData(byte[] bencodedRating) {
    try {
      Map mRating = StaticUtilities.getFormatters().bDecode(bencodedRating);
      this.score = Math.min(MAX_SCORE, ((Long) mRating.get("s")).intValue());
      this.nick  = new String((byte[]) mRating.get("n"),STRING_ENCODING);
      this.comment  = new String((byte[]) mRating.get("c"),STRING_ENCODING);      
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
  
  public byte[] encodes() {
    try {
      
      Map mRating = new HashMap();
      mRating.put("s", new Long(score));      
      //Avoid storing the nick (usually "Anonymous")
      //if no comment is left (saves a bit of space)
      if(comment.length() > 0) {
        mRating.put("n", nick.getBytes(STRING_ENCODING));      
      } else {
        mRating.put("n", "");
      }
      mRating.put("c", comment.getBytes(STRING_ENCODING));         
      return StaticUtilities.getFormatters().bEncode(mRating);
      
    } catch(Exception e) {
      e.printStackTrace();
    }
    return new byte[0];      
  }
  
  public int getScore() {
    return this.score;
  }
  
  public String getNick() {
    return this.nick;
  }
  
  public String getComment() {
    return this.comment;
  }
  
  public boolean
  needPublishing()
  {
	  return( score > 0 );
  }
}
