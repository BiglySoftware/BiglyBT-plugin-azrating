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

import java.util.ArrayList;
import java.util.List;


public class RatingResults {
  
  float averageRating;
  int   nbRatings;
  int   nbComments;
  StringBuffer  comments;
  
  List<RatingData>	rating_data;
  
  public RatingResults() {
    this.comments = new StringBuffer();
    nbComments = 0;
  }
  
  public synchronized void addRating(RatingData rating, boolean retain) {
    if(rating.getScore() > 0) {
    	
      if ( retain ){
    	if ( rating_data == null ){
    		rating_data = new ArrayList<RatingData>();
    	}
    	rating_data.add( rating );
      }
      averageRating = averageRating * nbRatings + rating.getScore();
      nbRatings++;
      averageRating = averageRating / nbRatings;
      
      String comment = rating.getComment();
      if(comment.length() > 0) {
      	nbComments++;
        comments.append(rating.getNick());
        comments.append(" :\n");
        comments.append(rating.getScore());
        comments.append("/5 . ");        
        comments.append(rating.getComment());
        comments.append("\n====================\n");
      }
    }
  }
  
  public List<RatingData>
  getRatings()
  {
	  return( rating_data );
  }
  
  public String getComments() {
    return comments.toString();
  }
  
  public int getAverageScoreRound() {
    return (int) (averageRating + 0.5);
  }
  
  public float getRealAverageScore() {
    return averageRating;
  }
  
  public String getAverageScore() {
    int average = (int) (averageRating * 10);
    return (average / 10) + "." + (average % 10);
  }
  
  public int getNbRatings() {
    return nbRatings;
  }
  
  public int getNbComments() {
    return nbComments;
  }
  
  public void setAverage(float average) {
    averageRating = average;
  }
  
}
