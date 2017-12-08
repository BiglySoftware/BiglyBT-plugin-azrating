/*
 * Created on Jun 28, 2013
 * Created by Paul Gardner
 * 
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */



package com.aelitis.azureus.plugins.rating.ui;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.ui.swt.pif.UISWTInstance;

import com.aelitis.azureus.plugins.rating.RatingPlugin;
import com.aelitis.azureus.plugins.rating.RatingUI;

public class 
RatingSWTUI 
	implements RatingUI
{
	private static final String COLUMN_ID_RATING = "RatingColumn";  

	private static String[] table_names = {
		TableManager.TABLE_MYTORRENTS_INCOMPLETE,
		TableManager.TABLE_MYTORRENTS_COMPLETE,
		TableManager.TABLE_MYTORRENTS_ALL_BIG,
		TableManager.TABLE_MYTORRENTS_COMPLETE_BIG,
		TableManager.TABLE_MYTORRENTS_INCOMPLETE_BIG,
		TableManager.TABLE_MYTORRENTS_UNOPENED,
		TableManager.TABLE_MYTORRENTS_UNOPENED_BIG,
	};

	private RatingPlugin		plugin;
	private UISWTInstance		swt_ui;
	
	private List<TableColumn>			table_columns 	= new ArrayList<TableColumn>();
	private List<TableContextMenuItem>	table_menus 	= new ArrayList<TableContextMenuItem>();
	private TableManager tm;

	public 
	RatingSWTUI(
		RatingPlugin	_plugin,
		UIInstance		_swt )
	{  
		plugin	= _plugin;
		swt_ui	= (UISWTInstance)_swt;

		RatingImageUtil.init(swt_ui.getDisplay());

		addMyTorrentsColumn();

		addMyTorrentsMenu();
	}
	  
	protected RatingPlugin
	getPlugin()
	{
		return( plugin );
	}
	
	protected UISWTInstance
	getSWTUI()
	{
		return( swt_ui );
	}
	  
	  private void addMyTorrentsColumn() {
	    RatingColumn ratingColumn = new RatingColumn(this);
	    for ( String table_name: table_names ){
	    	addRatingColumnToTable(table_name,ratingColumn);
	    }
	  }
	  
	  private void addRatingColumnToTable(String tableID,RatingColumn ratingColumn) {
		PluginInterface pluginInterface = plugin.getPluginInterface();
	    UIManager uiManager = pluginInterface.getUIManager();
	    TableManager tableManager = uiManager.getTableManager();
	    TableColumn activityColumn = tableManager.createColumn(tableID, COLUMN_ID_RATING);
	   
	    activityColumn.setAlignment(TableColumn.ALIGN_LEAD);
	    activityColumn.setPosition(5);
	    activityColumn.setWidth(95);
	    activityColumn.setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
	    activityColumn.setType(TableColumn.TYPE_GRAPHIC);
	    activityColumn.setVisible( true );
	    activityColumn.addListeners(ratingColumn);
	    
	    tableManager.addColumn(activityColumn);
	    
	    table_columns.add( activityColumn );
	  }
	  
	  private void addMyTorrentsMenu()  {
		  PluginInterface pluginInterface = plugin.getPluginInterface();
	    MenuItemListener  listener = 
	      new MenuItemListener()
	      {
	        @Override
	        public void
	        selected(
	          MenuItem    _menu,
	          Object      _target )
	        {
	          Download download = (Download)((TableRow)_target).getDataSource();
	          
	          if ( download == null || download.getTorrent() == null ){
	            
	            return;
	          }
	          
	          if ( !plugin.isRatingEnabled(download,false)){
	        	  return;
	          }
	          
	          if (swt_ui != null)
	          	new RatingWindow(plugin,swt_ui,download);
	        }
	      };

		    tm = pluginInterface.getUIManager().getTableManager();
			  for ( String table_name: table_names ){
		      TableContextMenuItem menu_item = tm.addContextMenuItem(table_name, "RatingPlugin.contextmenu.manageRating" );

		      menu_item.setDisposeWithUIDetach(UIInstance.UIT_SWT);
	    	  menu_item.addListener( listener );
	    	  menu_item.setHeaderCategory(MenuItem.HEADER_SOCIAL);
	    	  
	    	  table_menus.add( menu_item );
	      }
	   
	  }
	  
	  public UIInstance
	  getUI()
	  {
		  return( swt_ui );
	  }
	  
	  @Override
	  public void
	  destroy()
	  {
			for ( TableColumn c: table_columns ){
				
				c.remove();
			}
			
			table_columns.clear();
			
			for ( TableContextMenuItem m: table_menus ){
				
				m.remove();
			}
			
			table_menus.clear();

		  RatingImageUtil.destroy();
	  }
}
