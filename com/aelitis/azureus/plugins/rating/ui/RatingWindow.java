/*
 * Created on 14 mars 2005
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

import com.biglybt.ui.swt.imageloader.ImageLoader;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.utils.LocaleUtilities;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTInstance;

import com.aelitis.azureus.plugins.rating.RatingPlugin;
import com.aelitis.azureus.plugins.rating.updater.RatingData;
import com.aelitis.azureus.plugins.rating.updater.RatingResults;
import com.aelitis.azureus.plugins.rating.updater.RatingsUpdater;


public class RatingWindow {
	private static String MSG_PREFIX = "RatingPlugin.RatingWindow.";
	private static String MSG_SHELL_TITLE = MSG_PREFIX + "title";
	private static String MSG_GLOBAL_RATING = MSG_PREFIX + "globalRating";
	private static String MSG_PERSONAL_RATING = MSG_PREFIX + "personalRating";
	private static String MSG_BUTTON_CLEAR = MSG_PREFIX + "Button.clear";
	private static String MSG_BUTTON_OK = MSG_PREFIX + "Button.ok";
	private static String MSG_COMMENTS = MSG_PREFIX + "comments";
	private static String MSG_YOUR_COMMENT = MSG_PREFIX + "yourComment";
	private static String MSG_WARNING = MSG_PREFIX + "rateBeforeComment";
	private static String MSG_EDIT_TOOLTIP = MSG_PREFIX + "editTooltip";
  
  RatingPlugin   	plugin;
  UISWTInstance		swtUI;
  RatingsUpdater  updater;
  Download        download;
  
  Text txtPersonalComment;
  
  RatingResults   results;
  
  int score;
  String comment;
  
  Image lastRatingImage = null;
	private LocaleUtilities localeTxt;
  
  public RatingWindow(RatingPlugin plugin, UISWTInstance swtUI, Download download) {
    this.plugin = plugin;
    this.swtUI = swtUI;
    this.updater = plugin.getUpdater();
    this.download = download;
    localeTxt = plugin.getPluginInterface().getUtilities().getLocaleUtilities();

    results = updater.getRatingsForDownload(download);
    RatingData personalData = updater.loadRatingsFromDownload(download);
    score = personalData.getScore();
    comment = personalData.getComment();
    
    final Shell shell = new Shell();
    shell.setText(localeTxt.getLocalisedMessageText(MSG_SHELL_TITLE,
				new String[] { download.getTorrent().getName() }));
    if (!plugin.getPluginInterface().getUtilities().isOSX()) {
    	shell.setImage(ImageLoader.getInstance().getImage("logo16"));
    }
    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    shell.setLayout(layout);
    GridData data;
    
    Label lblGlobalRating  = new Label(shell,SWT.NONE);
    lblGlobalRating.setText(localeTxt.getLocalisedMessageText(MSG_GLOBAL_RATING));
    
    final Label lblGlobalRatingIcon = new Label(shell,SWT.NONE);
    
    Label lblGlobalRatingInfo = new Label(shell,SWT.None);
    lblGlobalRatingInfo.setText(results.getAverageScore());
    
    Label personalRating = new Label(shell,SWT.None);
    personalRating.setText(localeTxt.getLocalisedMessageText(MSG_PERSONAL_RATING));
    
    final Label personalRatingIcon = new Label(shell,SWT.NONE);    
    personalRatingIcon.addListener(SWT.MouseUp, new Listener() {
      @Override
      public void handleEvent(Event event) {
        score = Math.min(RatingData.MAX_SCORE, event.x
						/ RatingImageUtil.starWidth + 1);
        setRatingImage(personalRatingIcon,score);
      }
    });
    
    Button btnClear = new Button(shell,SWT.PUSH);
    btnClear.setText(localeTxt.getLocalisedMessageText(MSG_BUTTON_CLEAR));
    btnClear.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event event) {
        score = 0;
        setRatingImage(personalRatingIcon,score);
      }
    });
    
    personalRating.setToolTipText( MSG_EDIT_TOOLTIP );
    personalRatingIcon.setToolTipText( MSG_EDIT_TOOLTIP );
    
    Label lblComments = new Label(shell,SWT.NONE);
    lblComments.setText(localeTxt.getLocalisedMessageText(MSG_COMMENTS));
    
    StyledText txtComments = new StyledText(shell,SWT.V_SCROLL | SWT.BORDER | SWT.READ_ONLY);
    txtComments.setText(results.getComments());
    txtComments.setWordWrap(true);
    txtComments.setBackground( Colors.light_grey );
    data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 3;
    txtComments.setLayoutData(data);
    
    Label personalComment = new Label(shell,SWT.NONE);
    personalComment.setText(localeTxt.getLocalisedMessageText(MSG_YOUR_COMMENT));
    data = new GridData();    
    data.horizontalSpan = 3;
    personalComment.setLayoutData(data);
    
    txtPersonalComment = new Text(shell,SWT.BORDER);
    txtPersonalComment.setText(comment);
    data = new GridData(GridData.FILL_HORIZONTAL);
    data.horizontalSpan = 3;
    txtPersonalComment.setLayoutData(data);
    txtPersonalComment.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if(txtPersonalComment.getText().length() > 150 &&
            e.character != SWT.DEL &&
            e.keyCode   != 8) {          
          e.doit = false;
        }
      }
    });

    
    
    Button btnOk = new Button(shell,SWT.PUSH);
    btnOk.setText(localeTxt.getLocalisedMessageText(MSG_BUTTON_OK));
    data = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END);
    data.horizontalSpan = 3;
    data.widthHint = 70;
    btnOk.setLayoutData(data);
    
    btnOk.addListener(SWT.Selection,new Listener() {
      @Override
      public void handleEvent(Event event) {
        RatingData data = new RatingData(score,RatingWindow.this.plugin.getNick(),txtPersonalComment.getText());
        updater.storeRatingsToDownload(RatingWindow.this.download,data);
        shell.close();
      }
    });
    
    setRatingImage(lblGlobalRatingIcon,results.getRealAverageScore());
    setRatingImage(personalRatingIcon,score);
    
    lblGlobalRatingIcon.addDisposeListener(new DisposeListener() {
    	@Override
	    public void widgetDisposed(DisposeEvent e) {
      	Image img = lblGlobalRatingIcon.getImage();
      	if (img != null && !img.isDisposed())
      		img.dispose();
      	img = personalRatingIcon.getImage();
      	if (img != null && !img.isDisposed())
      		img.dispose();
    	}
    });
    personalRatingIcon.addDisposeListener(new DisposeListener() {
    	@Override
	    public void widgetDisposed(DisposeEvent e) {
      	Image img = personalRatingIcon.getImage();
      	if (img != null && !img.isDisposed())
      		img.dispose();
    	}
    });
    
    shell.setSize(350,400);
    
    Utils.centreWindow( shell );
    
    shell.open();
  }
  
  private void setRatingImage(Label label,float rating) {
  	Image img = label.getImage();
  	if (img != null && !img.isDisposed())
  		img.dispose();
  	
  	img = RatingImageUtil
				.createRatingImage(rating, swtUI.getDisplay());

    label.setImage(img);

    if(rating == 0) {
      if(txtPersonalComment.isEnabled()) {
        comment = txtPersonalComment.getText();
        txtPersonalComment.setText(localeTxt.getLocalisedMessageText(MSG_WARNING));
        txtPersonalComment.setEnabled(false);  
      }      
    } else {
      if(! txtPersonalComment.isEnabled()) {
        txtPersonalComment.setText(comment);
        txtPersonalComment.setEnabled(true);
      }
    }
  }
  
}
