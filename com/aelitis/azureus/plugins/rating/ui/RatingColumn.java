/*
 * Created on 12 mars 2005
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
package com.aelitis.azureus.plugins.rating.ui;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;

import com.aelitis.azureus.plugins.rating.RatingPlugin;
import com.aelitis.azureus.plugins.rating.updater.RatingData;
import com.aelitis.azureus.plugins.rating.updater.RatingResults;
import com.aelitis.azureus.plugins.rating.updater.RatingsUpdater;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.pif.utils.LocaleUtilities;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTGraphic;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.utils.SWTRunnable;

public class RatingColumn implements TableCellRefreshListener,
		TableCellDisposeListener, TableCellMouseListener {
  
  private RatingPlugin plugin;
  private RatingsUpdater updater;
  private UISWTInstance	swt_ui;
	private LocaleUtilities localeTxt;
  
  public RatingColumn(RatingSWTUI ui) {
    plugin = ui.getPlugin();
    swt_ui = ui.getSWTUI();
    
    localeTxt = plugin.getPluginInterface().getUtilities().getLocaleUtilities();
  }
  
  @Override
  public void refresh(TableCell cell) {
    Object dataSource = cell.getDataSource();
    if (dataSource == null || ! (dataSource instanceof Download)) {
        return; //opps something went wrong
    }        
    Download download = (Download) dataSource;
    
    if ( !plugin.isRatingEnabled(download, false )){
		return;
	}
    
    if(updater == null)
      updater = plugin.getUpdater();
    
    
    String toolTip = null;
    
    float average = 0;
    float personalScore = 0;
    
    if (updater != null) {
			RatingResults rating = updater.getRatingsForDownload(download);

			if (rating != null) {
				average = rating.getRealAverageScore();
				average += (float) rating.getNbRatings() / 10000;
				if (rating.getNbRatings() > 0) {
					toolTip = localeTxt.getLocalisedMessageText(
							"RatingPlugin.tooltip.avgRating", new String[] {
									"" + rating.getAverageScore(), "" + rating.getNbRatings() });
				} else {
					toolTip = localeTxt.getLocalisedMessageText(
							"RatingPlugin.tooltip.noRating", new String[] { ""
									+ rating.getAverageScore() });
				}

				int nbComments = rating.getNbComments();
				if (nbComments > 0) {
					toolTip += "\n"
							+ localeTxt.getLocalisedMessageText(
									"RatingPlugin.tooltip.numComments", new String[] { ""
											+ rating.getNbComments() });
				}

			}

			RatingData personalData = updater.loadRatingsFromDownload(download);
			if (personalData != null) {
				personalScore = personalData.getScore();
				if (personalScore > 0)
					toolTip += "\n"
							+ localeTxt.getLocalisedMessageText(
									"RatingPlugin.tooltip.yourRating", new String[] { ""
											+ personalScore });
			}
			
	  	if (swt_ui == null) {
	  		cell.setText(rating.getAverageScore() + "/" + "5.0");
	  	}
		}
    
    if (!cell.setSortValue(average) && cell.isValid())
      return;

  	if (swt_ui != null) {
	    Image image = RatingImageUtil.createStarLineImage(average, personalScore,
					swt_ui.getDisplay());
	    int cellWidth = cell.getWidth();
	  	if (cellWidth > 0 && cellWidth < image.getBounds().width) {
	  		ImageData data = image.getImageData(); 
	  		image.dispose();
	  		data = data.scaledTo(cell.getWidth(), data.height);
	  		image = new Image(swt_ui.getDisplay(), data);
	  	}
			Graphic graphic = swt_ui.createGraphic(image);

	    // dispose of previous graphic
	    dispose(cell);
	    cell.setGraphic(graphic);
  	}
    
    cell.setToolTip(toolTip);
  }

	@Override
	public void dispose(TableCell cell)
	{
	    Graphic g = cell.getGraphic();
	    if (g instanceof UISWTGraphic) {
	    	final Image img = ((UISWTGraphic)g).getImage();
	    	if (img != null && !img.isDisposed()){
	    		Utils.execSWTThread(
    				new SWTRunnable(){
					    @Override
					    public void runWithDisplay(Display display) {
    						if ( !img.isDisposed()){
    							
    							img.dispose();
    						}
    					}
    				});
	    	}
    	}
	}

	@Override
	public void cellMouseTrigger(TableCellMouseEvent event) {
		Object dataSource = event.cell.getDataSource();
		if (!(dataSource instanceof Download))
			return;

		Download download = (Download) dataSource;
		
		if ( !plugin.isRatingEnabled(download, false)){
			return;
		}
		
		// middle button
		if (event.eventType == TableCellMouseEvent.EVENT_MOUSEDOWN
				&& event.button == 2) {
			try {
				int cell_width = event.cell.getWidth();
				
				int	all_stars = RatingImageUtil.starWidth*5;
				
				int padding;
				int	star_width;
				
				if ( cell_width >= all_stars ){
					
					padding = (event.cell.getWidth() - all_stars)/2;
					
					star_width = RatingImageUtil.starWidth;
					
				}else{
					
					padding = 0;
					
					star_width = cell_width/5;
				}
				
				int score = ( event.x - padding )/ star_width + 1;

				if (updater == null)
					updater = plugin.getUpdater();
	
				RatingData oldData = updater.loadRatingsFromDownload(download);
	
				RatingData data = new RatingData(score, oldData.getNick(), oldData
						.getComment());
				updater.storeRatingsToDownload(download, data);
				event.cell.invalidate();
			} catch (Exception e) {
				plugin.logError("Set personal rating via cell click", e);
			}
		} else if (event.eventType == TableCellMouseEvent.EVENT_MOUSEDOUBLECLICK
				&& swt_ui != null) {
			try {
				new RatingWindow(plugin, swt_ui, download);
				event.skipCoreFunctionality = true;
			} catch (Exception e) {
				plugin.logError("Open RatingWidnow via cell click", e);
			}
		}
	}
}
