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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoSet;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.ddb.DistributedDatabaseEvent;
import com.biglybt.pif.ddb.DistributedDatabaseKey;
import com.biglybt.pif.ddb.DistributedDatabaseListener;
import com.biglybt.pif.ddb.DistributedDatabaseValue;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadAnnounceResult;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.download.DownloadManagerListener;
import com.biglybt.pif.download.DownloadScrapeResult;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.content.ContentException;
import com.biglybt.core.content.RelatedContent;
import com.biglybt.core.content.RelatedContentLookupListener;
import com.biglybt.core.content.RelatedContentManager;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.MovingImmediateAverage;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatInstance;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.ChatMessage;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.aelitis.azureus.plugins.rating.RatingPlugin;

public class 
RatingsUpdater 	
	implements DownloadManagerListener, ParameterListener
{
	private static final int READ_PUBLIC_TIMEOUT 		= 20*1000;
	private static final int READ_NON_PUBLIC_TIMEOUT 	= 80*1000;
	
    private static final int WRITE_DELAY			= 10*1000;
    private static final int READ_DELAY				= 20*1000;
    
    private static final int READ_BY_HASH_TIMEOUT	= 30*1000;
    
    private static final int COMPLETE_DOWNLOAD_LOOKUP_PERIOD 			= 8*60*60*1000;
    private static final int COMPLETE_DOWNLOAD_NO_SEEDS_LOOKUP_PERIOD 	= 2*60*60*1000;
    private static final int INCOMPLETE_OLD_DOWNLOAD_LOOKUP_PERIOD		= 4*60*60*1000;
    private static final int INCOMPLETE_DOWNLOAD_LOOKUP_PERIOD 			= 1*60*60*1000;
    
    private static final int TIMER_CHECK_PERIOD		= 2*60*1000;
    private static final int STALL_MIN_DL_AGE		= 12*60*60*1000;
    private static final int STALL_TRIGGER_PERIOD	= 2*60*60*1000;
    
    private static final Object	DOWNLOAD_CHAT_KEY = new Object();
    
	private final RatingPlugin 		plugin;
	private final PluginInterface	plugin_interface;
	
	private DistributedDatabase 	database_public;  
  	
	private TorrentAttribute attributeRating;
	private TorrentAttribute attributeComment;
	private TorrentAttribute attributeGlobalRating;
	private TorrentAttribute attributeChatState;

  
	private Map<Download,RatingResults> 	torrentRatings	= new HashMap<Download, RatingResults>();

	private LinkedList<Download>	downloads_to_publish 	= new LinkedList<Download>();
	private Map<Download,Long>		downloads_to_lookup		= new HashMap<Download,Long>();
	
	private AsyncDispatcher		write_dispatcher 	= new AsyncDispatcher( "rating:write" );
	private AsyncDispatcher		read_dispatcher 	= new AsyncDispatcher( "read" );
	
	private boolean	write_in_progress;
	private boolean	read_in_progress;
	
	private TimerEventPeriodic		timer;
	private Object					reseed_bytes_key = new Object();
	private Object					reseed_asked_key = new Object();
	
	private volatile boolean enable_auto_chat;
	
	private volatile boolean	destroyed;
	
	public 
	RatingsUpdater(
		RatingPlugin _plugin ) 
	{    
		plugin 				= _plugin; 
		plugin_interface	= plugin.getPluginInterface();
	   
		TorrentManager tm = plugin_interface.getTorrentManager();
		
	    attributeRating  		= tm.getPluginAttribute("rating");
	    attributeComment		= tm.getPluginAttribute("comment");    
	    attributeGlobalRating  	= tm.getPluginAttribute("globalRating");
	    attributeChatState  	= tm.getPluginAttribute("chatState");
	    
	    COConfigurationManager.addAndFireParameterListener( "azbuddy.dchat.ui.enable.auto.dl.chat", this );
	}
	  
	public void 
	initialize() 
	{
		plugin_interface.getUtilities().createThread(
				"Initializer", 
				new Runnable() 
				{
					@Override
					public void
					run() 
					{
						database_public = plugin_interface.getDistributedDatabase();

						if ( !destroyed ){
							
							DownloadManager download_manager = plugin_interface.getDownloadManager();
							
							download_manager.addListener( RatingsUpdater.this, false );
							
							Download[] existing = download_manager.getDownloads();
							
							for ( Download d: existing ){
								
								downloadAdded( d, false );
							}
							
							timer = SimpleTimer.addPeriodicEvent(
								"ratings:checker",
								TIMER_CHECK_PERIOD,
								new TimerEventPerformer()
								{
									@Override
									public void
									perform(
										TimerEvent event) 
									{
										if ( destroyed ){
											
											TimerEventPeriodic t = timer;
											
											timer = null;
											
											if ( t != null ){
											
												t.cancel();
											}
											
											return;
										}
										
										try{
											RelatedContentManager rcm = RelatedContentManager.getSingleton();
											
											checkStalls( rcm );
											
										}catch( Throwable e ){
										}	
									}
								});
						}
					}
				});  
	}

	@Override
	public void 
	parameterChanged(
		String parameterName )
	{
		enable_auto_chat = COConfigurationManager.getBooleanParameter( "azbuddy.dchat.ui.enable.auto.dl.chat", true );
	}
	
	public void
	destroy()
	{
		destroyed = true;
		
		DownloadManager download_manager = plugin_interface.getDownloadManager();
		
		download_manager.removeListener( this );
		
		TimerEventPeriodic t = timer;
		
		timer = null;
		
		if ( t != null ){
		
			t.cancel();
		}
		
	    COConfigurationManager.removeParameterListener( "azbuddy.dchat.ui.enable.auto.dl.chat", this);
	}
	
	@Override
	public void
	downloadAdded(
		Download download )
	{   
		downloadAdded( download, true );
	}
	
	private void 
	downloadAdded(
		Download 	download,
		boolean		is_new )
	{    
		if ( destroyed ){
			
			return;
		}
		
		if ( plugin.isRatingEnabled(download, true)){
			
			if ( loadRatingsFromDownload( download ).needPublishing()){
				
				addForPublish( download, false );
				
			}else{
				
				addForLookup( download, is_new );
			}
		}
	}

	@Override
	public void
	downloadRemoved(
		Download download ) 
	{
		synchronized( torrentRatings ){
		
			torrentRatings.remove( download );
			
			downloads_to_publish.remove( download );
			
			downloads_to_lookup.remove( download );
		}
	}

	private void
	checkStalls(
		RelatedContentManager		rcm )
	{
		if ( !rcm.isEnabled()){
			
			rcm = null;
		}
		
		DownloadManager download_manager = plugin_interface.getDownloadManager();

		long	real_now = SystemTime.getCurrentTime();
		long	mono_now = SystemTime.getMonotonousTime();

		List<Download> interesting = new ArrayList<Download>(10);
		
		for ( final Download download: download_manager.getDownloads()){
						
			com.biglybt.core.download.DownloadManager core_dm = PluginCoreUtils.unwrap( download );

			if ( download.getState() != Download.ST_DOWNLOADING ){
				
				continue;
			}
				
			PEPeerManager pm = core_dm.getPeerManager();
			
			if ( pm == null ){
				
				continue;
			}
			
			if ( 	download.getFlag( Download.FLAG_METADATA_DOWNLOAD ) ||
					download.getFlag( Download.FLAG_LOW_NOISE )){
				
				continue;
			}
			
			interesting.add( download );
		}
		
		Collections.sort(
			interesting,
			new Comparator<Download>()
			{
				@Override
				public int
				compare(
					Download o1, 
					Download o2) 
				{
					return( o1.getPosition() - o2.getPosition());
				}
			});
		
		for ( int i=0;i<interesting.size();i++){
		
			Download	download = interesting.get( i );
					
			com.biglybt.core.download.DownloadManager core_dm = PluginCoreUtils.unwrap( download );

			PEPeerManager pm = core_dm.getPeerManager();
			
			if ( pm == null ){
				
				continue;
			}
			
			checkReseed( real_now, mono_now, interesting, i, download, core_dm, pm );
			
			if ( rcm != null ){
				
				checkAlternativeDownloads( rcm, real_now, mono_now, download, core_dm, pm );
			}
		}
	}
	
	private void
	checkReseed(
		long												real_now,
		long												mono_now,
		List<Download>										interesting,
		int													interesting_index,
		final Download										download,
		com.biglybt.core.download.DownloadManager 	core_dm,
		PEPeerManager										pm )
	{
		boolean	actively_monitoring = false;

		try{
			if ( core_dm.getUserData( reseed_asked_key ) != null ){
				
				return;
			}
			
			long date_added = core_dm.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );
			
			if ( date_added <= 0 ){
				
				return;
			}
			
			if ( real_now - date_added < STALL_MIN_DL_AGE ){
				
				return;
			}
			
			actively_monitoring = true;
			
			long downloaded = core_dm.getStats().getTotalDataBytesReceived();
			
			long[] old_entry = (long[])core_dm.getUserData( reseed_bytes_key );
			
			if ( old_entry == null || old_entry[1] < downloaded ){
				
				core_dm.setUserData( reseed_bytes_key, new long[]{ mono_now, downloaded });

				return;
			}
			
			long elapsed_since_dl = mono_now - old_entry[0];
			
			if ( elapsed_since_dl < STALL_TRIGGER_PERIOD ){
				
				return;
			}
			
				// check that either this is at the top of the download queue or that all prior to 
				// it have been flagged as stuck already as they are higher priority and will be 
				// stealing download capacity
			
			for ( int i=0;i<interesting_index;i++){
				
				Download prev = interesting.get( i );
				
				com.biglybt.core.download.DownloadManager core_prev = PluginCoreUtils.unwrap( prev );

				if ( core_prev.getUserData( reseed_asked_key ) == null ){

						// bail for the moment, we'll get back here later if needed
					
					return;
				}
			}
			
			List<PEPeer> peers = pm.getPeers();
			
			boolean	has_connected_peers = false;
			
			for ( PEPeer peer: peers ){
				
				if ( peer.getPeerState() == PEPeer.TRANSFERING ){
					
					has_connected_peers = true;
					
					break;
				}
			}
			
				// if we're connected to peers and stuck then report
			
			if ( !has_connected_peers ){
				
					// sanity check - user may have well seeded torrent but borked network
				
				DownloadScrapeResult sr = download.getAggregatedScrapeResult();
				
				boolean	poor_swarm = false;
				
				if ( sr != null && sr.getResponseType() == DownloadScrapeResult.RT_SUCCESS ){
					
					int seeds_peers = sr.getSeedCount() + sr.getNonSeedCount();
					
					if ( seeds_peers > 16 ){
						
						actively_monitoring = false;
						
						return;
					}
					
					poor_swarm = true;
				}
				
				DownloadAnnounceResult ar = download.getLastAnnounceResult();
				
				if ( ar != null && ar.getResponseType() == DownloadAnnounceResult.RT_SUCCESS ){
					
					int seeds_peers = ar.getSeedCount() + ar.getNonSeedCount();
					
					if ( seeds_peers > 16 ){
						
						actively_monitoring = false;
						
						return;
					}
					
					poor_swarm = true;
				}
				
				if ( !poor_swarm ){
					
						// don't know swarm state yet so don't know whether to report or not
					
					return;
				}
			}
			
				// download made no progress in the last interval
			
			DiskManagerFileInfoSet file_set = core_dm.getDiskManagerFileInfoSet();
			
			DiskManagerFileInfo[] files = file_set.getFiles();			
			
			DiskManagerFileInfo	worst_file 	= null;
			float				worst_avail	= Float.MAX_VALUE;
			
			for ( DiskManagerFileInfo file: files ){
				
				if ( ( !file.isSkipped()) && file.getDownloaded() < file.getLength()){
					
					float file_avail = pm.getMinAvailability( file.getIndex());
					
					if ( file_avail < 1.0 ){
						
						if ( file_avail < worst_avail ){
							
							worst_avail = file_avail;
							worst_file	= file;
							
						}else if ( file_avail == worst_avail ){
							
							if ( worst_file != null && worst_file.getLength() < file.getLength()){
								
								worst_file = file;
							}
						}
					}
				}
			}
			
			if ( worst_file != null ){
				
				core_dm.setUserData( reseed_asked_key, "" );
				
				final String msg_prefix = "Please seed! File '";
				final String file_name	= worst_file.getTorrentFile().getRelativePath().replace( '\\', '/' );
				
				final String message 	= msg_prefix + file_name + "' is stuck with an availability of " + worst_avail + " :(";
				
				if ( BuddyPluginUtils.isBetaChatAvailable()){
					
					chat_write_dispatcher.dispatch(
						new AERunnable() {
							
							@Override
							public void 
							runSupport() 
							{
								if ( !enable_auto_chat ){
									
									return;
								}

								final BuddyPluginBeta.ChatInstance chat = getChatForDownload( download );
									
								if ( chat != null ){
										
									chat.setAutoNotify( true );
									
									waitForChat(
										chat,
										new AERunnable()
										{
											@Override
											public void 
											runSupport() 
											{
												long now = SystemTime.getCurrentTime();
												
												List<ChatMessage>	messages = chat.getMessages();
												
												for ( ChatMessage message: messages ){
													
													String msg = message.getMessage();
													
													if ( message.getParticipant().isMe()){
														
														if ( msg.startsWith( msg_prefix )){
																															
															return;
														}
													}else{
														
															// someone else recently reported?
														
														if ( msg.startsWith( msg_prefix )){
															
															if ( msg.replace( '\\', '/' ).contains( file_name )){
														
																if ( now - message.getTimeStamp() < 12*60*60*1000 ){
																																		
																	return;
																}
															}
														}
													}
												}
												
												Map<String,Object>	flags 	= new HashMap<String, Object>();
												
												flags.put( BuddyPluginBeta.FLAGS_MSG_ORIGIN_KEY, BuddyPluginBeta.FLAGS_MSG_ORIGIN_RATINGS );
												
												Map<String,Object>	options = new HashMap<String, Object>();
												
												chat.setSharedNickname( false );
												
												chat.sendMessage( message, flags, options );																									
											}
										});
									}
								}
							});
				}
			}else{
				
				actively_monitoring = false;
			}
		}finally{
			
			if ( !actively_monitoring ){
				
				core_dm.setUserData( reseed_bytes_key, null );
			}
		}
	}
	
	private static final int ALT_AVAIL_MIN_PERIOD			= 16*60*1000;
	private static final int ALT_AVAIL_AVERAGE_SPEED_PERIOD	= 16*60*1000;
	private static final int ALT_AVAIL_RETRY_PERIOD			= 60*60*1000;
	
	private static final long 	ALT_AVAIL_MIN_FILE_SIZE 	= 75*1024*1024L;
	private static final int	ALT_AVAIL_SLOW_RATE			= 10*1024;

	
	private static final Object	alt_dl_average_key 		= new Object();
	private static final Object	alt_dl_done_files_key 	= new Object();
	private static final Object	alt_dl_done_key 		= new Object();
		
	private void
	checkAlternativeDownloads(
		RelatedContentManager								rcm,
		long												real_now,
		long												mono_now,
		final Download										download,
		com.biglybt.core.download.DownloadManager 	core_dm,
		PEPeerManager										pm )
	{
		boolean	actively_monitoring = false;
		
		try{
			Long		last_done = (Long)core_dm.getUserData( alt_dl_done_key );

			if ( last_done != null ){
				
				if ( real_now - last_done < ALT_AVAIL_RETRY_PERIOD ){
					
					return;
				}
				
				core_dm.setUserData( alt_dl_average_key, null );
				core_dm.setUserData( alt_dl_done_files_key, null );
				core_dm.setUserData( alt_dl_done_key, null );
			}
						
			int	global_down_limit 	= plugin_interface.getPluginconfig().getCoreIntParameter( PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC );

			if ( global_down_limit > 0 ){
					
					// could be smarted about this - we really want to know if the downloads download rate
					// has been affected by a limit in the last period but this information isn't
					// readily available...
				
				long current_rate = plugin_interface.getDownloadManager().getStats().getSmoothedReceiveRate();
				
					// bail if we're currently near the limit
				
				if ( current_rate >= global_down_limit - 5*1024 ){
										
					return;
				}
			}
			
			int	dl_limit = download.getDownloadRateLimitBytesPerSecond();
			
			if ( dl_limit < 0 || ( dl_limit > 0 && dl_limit < ALT_AVAIL_SLOW_RATE + 5*1024 )){
				
				return;
			}
			
			actively_monitoring = true;
			
			long downloaded = core_dm.getStats().getTotalDataBytesReceived();
	
			Object[] info =  (Object[])core_dm.getUserData( alt_dl_average_key );
			
			if ( info == null ){
				
				MovingImmediateAverage av = AverageFactory.MovingImmediateAverage( ALT_AVAIL_AVERAGE_SPEED_PERIOD / TIMER_CHECK_PERIOD );
				
				core_dm.setUserData( alt_dl_average_key, new Object[]{ av, downloaded });
				
				return;
			}
			
			MovingImmediateAverage 	average = (MovingImmediateAverage)info[0];
			long					last_dl	= (Long)info[1];
			
			long	diff = downloaded - last_dl;
			
			if ( diff < 0 ){
				
				diff = 0;
			}
			
			long bytes_per_period = (long)average.update( diff );
			
			info[1] = downloaded;
			
			long bytes_per_second = bytes_per_period/(TIMER_CHECK_PERIOD/1000);
						
			long added_time = core_dm.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );
			
			if ( real_now - added_time < ALT_AVAIL_MIN_PERIOD ){
				
				return;
			}
			
			if ( average.getSampleCount() < average.getPeriods()){
								
				return;
			}
	
			
			if ( bytes_per_second <= ALT_AVAIL_SLOW_RATE ){
				
				DiskManagerFileInfoSet file_set = core_dm.getDiskManagerFileInfoSet();
				
				DiskManagerFileInfo[] files = file_set.getFiles();			
					
				DiskManagerFileInfo	file_to_check 		= null;
				long				file_to_check_size	= 0;
				
				int					possible_files		= 0;
				
				List<Integer>	done_files = (List<Integer>)core_dm.getUserData( alt_dl_done_files_key );
				
				if ( done_files == null ){
					
					done_files = new ArrayList<Integer>();
					
					core_dm.setUserData( alt_dl_done_files_key, done_files );
				}
				
				for ( DiskManagerFileInfo file: files ){
					
					long	file_size = file.getLength();
					
					if ((!file.isSkipped()) && file.getDownloaded() < file_size ){
						
						if ( file_size >= ALT_AVAIL_MIN_FILE_SIZE ){
							
							Integer	file_index = file.getIndex();
							
							if ( !done_files.contains( file_index )){
								
								possible_files++;
								
								if ( file_size > file_to_check_size ){
									
									file_to_check 		= file;
									file_to_check_size	= file_size;
								}
							}
						}
					}
				}
				
				if ( file_to_check == null ){
					
					core_dm.setUserData( alt_dl_done_key, real_now );
					
				}else{
					
					done_files.add( file_to_check.getIndex());
					
					if ( possible_files == 1 || done_files.size() >= 3 ){
						
						core_dm.setUserData( alt_dl_done_key, real_now );
					}
					
					if ( BuddyPluginUtils.isBetaChatAvailable()){

						final DiskManagerFileInfo	target_file = file_to_check;
						
						try{
							rcm.lookupContent(
								target_file.getLength(),
								core_dm.getDownloadState().getNetworks(),
								new RelatedContentLookupListener() 
								{
									private List<RelatedContent>	content = new ArrayList<RelatedContent>();
									
									@Override
									public void
									lookupStart() 
									{
									}
									
									@Override
									public void
									lookupFailed(
										ContentException 	error ) 
									{
									}
									
									@Override
									public void
									lookupComplete() 
									{
										altContentFound( download, target_file, content );
									}
									
									@Override
									public void
									contentFound(
										RelatedContent[] new_content ) 
									{
										synchronized( content ){
										
											content.addAll( Arrays.asList( new_content ));
										}
										
									}
								});
						}catch( Throwable e ){
						}
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
			
		}finally{
			
			if ( !actively_monitoring ){
				
				core_dm.setUserData( alt_dl_average_key, null );
			}
		}
	}
	
	private void
	altContentFound(
		final Download				download,
		final DiskManagerFileInfo	file,
		List<RelatedContent>		_contents )
	{
		final List<RelatedContent> contents = new ArrayList<RelatedContent>( _contents );
		
		byte[] dl_hash = download.getTorrentHash();
		
		Iterator<RelatedContent> it = contents.iterator();
			
		while( it.hasNext()){
			
			RelatedContent content = it.next();
			
			byte[] hash = content.getHash();
			
			if ( hash == null || Arrays.equals( hash, dl_hash )){
				
				it.remove();
			}
		}
		
		if ( contents.size() == 0 ){
			
			return;
		}
		
		chat_write_dispatcher.dispatch(
			new AERunnable() 
			{	
				@Override
				public void 
				runSupport() 
				{
					if ( !enable_auto_chat ){
						
						return;
					}

					final ChatInstance chat = getChatForDownload( download );
											
					if ( chat != null ){
							
						chat.setAutoNotify( true );
							
						waitForChat(
							chat,
							new AERunnable() {
								
								@Override
								public void 
								runSupport() 
								{
									altContentWrite( chat, download, file, contents );
								}
							});
						
						if ( Constants.isCVSVersion()){
							
							try{
									
								final ChatInstance stats_chat = BuddyPluginUtils.getChat( chat.getNetwork(), "Statistics: Files: Duplicates" );
								
								if ( stats_chat != null ){
									
									stats_chat.setSharedNickname( false );
									
									Map<String,Object>	flags 	= new HashMap<String, Object>();
									
									flags.put( BuddyPluginBeta.FLAGS_MSG_ORIGIN_KEY, BuddyPluginBeta.FLAGS_MSG_ORIGIN_RATINGS );
									
									Map<String,Object>	options = new HashMap<String, Object>();
	
									stats_chat.sendMessage( String.valueOf( file.getLength()), flags, options );
									
									SimpleTimer.addEvent(
										"Rating:chat:closer",
										SystemTime.getCurrentTime() + 15*60*1000,
										new TimerEventPerformer(){
											
											@Override
											public void perform(TimerEvent event){
											
												stats_chat.destroy();
											}
										});
								}
							}catch( Throwable e ){
							}
						}
					}
				}
			});
	}
	
	private void
	altContentWrite(
		ChatInstance			chat,
		Download				download,
		DiskManagerFileInfo		file,
		List<RelatedContent>	contents )
	{
		List<ChatMessage>	messages = chat.getMessages();
		
		Map<String,String>	msg_map = new HashMap<String, String>();
		
		for ( RelatedContent rc: contents ){
			
			byte[]	hash = rc.getHash();
			
			String title = rc.getTitle();
			
			boolean title_truncated = false;
			
			while( title.length() > 32 ){
				
				int enc_len = UrlUtils.encode( title ).getBytes( Constants.UTF_8 ).length;
			
				if ( enc_len > 150 ){
					
					title_truncated = true;
					
					title = title.substring( 0, title.length() - 1 );
					
					if ( Character.isHighSurrogate( title.charAt( title.length()-1 ))){
						
						title = title.substring( 0, title.length() - 1 );
					}
				}else{
					
					break;
				}
			}
			
			if ( title_truncated ){
				
				title = title + "...";
			}
			
			String uri = UrlUtils.getMagnetURI( hash, title, rc.getNetworks());
			
			String[] tags = rc.getTags();
			
			if ( tags != null ){
				
				for ( String tag: tags ){
					
					uri += "&tag=" + UrlUtils.encode( tag );
				}
			}
			
			uri += "[[$dn]]";
			
			String file_str;
			
			if ( download.getDiskManagerFileCount() == 1 ){
			
				file_str = "this file";
				
			}else{
			
				String path = file.getTorrentFile().getRelativePath();
				
				boolean path_truncated = false;
				
				while( path.length() > 32 ){
					
					int enc_len = path.getBytes( Constants.UTF_8 ).length;
				
					if ( enc_len > 150 ){
						
						path_truncated = true;
						
						path = path.substring( Character.isHighSurrogate( path.charAt(0))?2:1 );
						
					}else{
						
						break;
					}
				}
				
				if ( path_truncated ){
					
					path = "..." + path;
				}
								
				file_str = "file '" + path + "'";
			}
			
			String msg = "Download " + uri + " may also contain " + file_str;
			
			String 	suffix = "";
			String	separator	= "";
			
			if ( rc.getSeeds() >= 0 ){
				suffix += "seeds=" + rc.getSeeds();
				separator = ", ";
			}
			if ( rc.getLeechers() >= 0 ){
				suffix +=separator + "peers=" + rc.getLeechers();
				separator = ", ";
			}		
			if ( rc.getSize() > 0){
				suffix +=separator + "size=" + DisplayFormatters.formatByteCountToKiBEtc( rc.getSize());
				separator = ", ";
			}
			
			if ( suffix.length() > 0 ){
				
				msg += " (" + suffix + ")";
			}
			
			msg_map.put( Base32.encode( hash ), msg );
		}
		
		boolean has_info = false;
		
		for ( ChatMessage message: messages ){
			
			if ( msg_map.size() == 0 ){
				
				return;
			}
			
			String chat_msg = message.getMessage();
			
			if ( chat_msg.contains( "http://wiki.vuze.com/w/Swarm_Merging" ) || chat_msg.contains( "http://wiki.biglybt.com/w/Swarm_Merging" )){
				
				has_info = true;
			}
			
			Iterator<String> it = msg_map.keySet().iterator();
			
			while( it.hasNext()){
				
				if ( chat_msg.contains( it.next())){
					
					it.remove();
				}
			}
		}
		
		if ( msg_map.size() == 0 ){
			
			return;
		}

		Map<String,Object>	flags 	= new HashMap<String, Object>();
		
		flags.put( BuddyPluginBeta.FLAGS_MSG_ORIGIN_KEY, BuddyPluginBeta.FLAGS_MSG_ORIGIN_RATINGS );
		
			// update oneday to use constants
		
		flags.put( "f", 1 );

		Map<String,Object>	options = new HashMap<String, Object>();
		
		chat.setSharedNickname( false );
		
		for ( String msg: msg_map.values()){
			
			chat.sendMessage( msg, flags, options );
		}
		
		if ( !has_info ){
		
			chat.sendMessage( "See http://wiki.biglybt.com/w/Swarm_Merging[[Swarm%20Merging]] for help", flags, options);
		}
	}
	
	private void
	waitForChat(
		final ChatInstance		chat,
		final AERunnable		runnable )
	{
			// wait for chat to synchronize and then run 
		
		final TimerEventPeriodic[] event = { null };
		
		synchronized( event ){
			
			event[0] = 
				SimpleTimer.addPeriodicEvent(
					"Rating:chat:checker",
					30*1000,
					new TimerEventPerformer()
					{
						private int elapsed_time;
	
						@Override
						public void
						perform(
							TimerEvent e ) 
						{
							elapsed_time += 30*1000;
																													
							if ( chat.isDestroyed()){
									
								synchronized( event ){
								
									event[0].cancel();
								}
								
							}else{
							
								if ( 	chat.getIncomingSyncState() == 0 ||
										elapsed_time >= 5*60*1000 ){
										
									synchronized( event ){
										
										event[0].cancel();
									}
								
									SimpleTimer.addEvent(
										"Rating:chat:checker",
										SystemTime.getOffsetTime( 2*60*1000 ),
										new TimerEventPerformer()
										{	
											@Override
											public void
											perform(
												TimerEvent event ) 
											{
												if ( !chat.isDestroyed()){
													
													chat_write_dispatcher.dispatch( 
														new AERunnable() {
															
															@Override
															public void 
															runSupport() 
															{
																if ( !chat.isDestroyed()){
																	
																	runnable.runSupport();
																}
															}
														});
												}
											}
										});
								}
							}
						}
					});	
		}
	}
	
	private void
	addForLookup(
		Download	download,
		boolean		is_priority )
	{
		if ( download.isRemoved()){
			
			downloadRemoved( download );
			
			return;
		}
		
		synchronized( torrentRatings ){
			
			Long data = downloads_to_lookup.get( download );
			
			if ( data == null || is_priority ){
				
				downloads_to_lookup.put( download, is_priority?-1L:0L );
			}
		}
		
		read_dispatcher.dispatch(
			new AERunnable()
			{
				private AERunnable dispatcher = this;

				@Override
				public void
				runSupport()
				{
					if ( destroyed ){
						
						return;
					}
					
					if ( read_dispatcher.getQueueSize() > 0 ){
						
						return;
					}
					
					Download 	next_download 	= null;
					
					long	now = SystemTime.getCurrentTime();
					
					int	num_ready = 0;
					
					synchronized( torrentRatings ){
						
						if ( read_in_progress || downloads_to_lookup.size() == 0 ){
							
							return;
						}
						
						long		next_lookup		= 0;
						Download	priority_dl		= null;
						
						for ( Map.Entry<Download, Long> entry: downloads_to_lookup.entrySet()){
							
							Download 	dl 			= (Download)entry.getKey();
							long		last_lookup = (Long)entry.getValue();
							
							if ( last_lookup == -1 ){
								
									// priority
								
								num_ready++;
								
								next_download 	= dl;
								next_lookup		= now;
								
								priority_dl = dl;
																
							}else if ( last_lookup == 0 ){
								
									// first time
								
								num_ready++;
								
								if ( next_download == null || next_lookup > now ){
									
									next_download 	= dl;
																		
								}else{
									
									if ( !dl.isComplete( false )){
										
										next_download = dl;
									}
								}
								
								next_lookup		= now;

							}else{
								
								long	next = last_lookup;
								
								if ( dl.isComplete( false )){
									
									int delay = COMPLETE_DOWNLOAD_LOOKUP_PERIOD;
									
									if ( dl.getState() == Download.ST_STOPPED ){
									
										DownloadScrapeResult res = dl.getAggregatedScrapeResult();
										
										if ( res != null && res.getSeedCount() == 0 ){
											
												// check for possible stalled dl information more frequently
											
											delay = COMPLETE_DOWNLOAD_NO_SEEDS_LOOKUP_PERIOD;
										}
									}
									
									next += delay;
									
								}else{
									
									com.biglybt.core.download.DownloadManager	core_download = PluginCoreUtils.unwrap( dl );
									
									long added = core_download.getDownloadState().getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);

									if ( added > 0 && now - added > 24*60*60*1000 ){
										
										next += INCOMPLETE_OLD_DOWNLOAD_LOOKUP_PERIOD;
										
									}else{
									
										next += INCOMPLETE_DOWNLOAD_LOOKUP_PERIOD;
									}
								}
								
								if ( next <= now ){
									
									num_ready++;
								}
								
								if ( next_lookup == 0 || next < next_lookup ){
									
									next_lookup 	= next;
									next_download	= dl;
								}
							}
						}
						
						if ( priority_dl != null ){
							
							next_download 	= priority_dl;
							next_lookup		= now;
						}
						
						if ( next_lookup > now ){
							
							log( "Lookup sleeping for " + ( next_lookup - now ));
							
							SimpleTimer.addEvent(
									"Rating:read:next",
									next_lookup,
									new TimerEventPerformer()
									{
										@Override
										public void
										perform(
											TimerEvent event )
										{														
											read_dispatcher.dispatch( dispatcher );
										}
									});
							
							next_download = null;
							
						}else{
							
							if ( next_download != null ){
								
								downloads_to_lookup.put( next_download, now );
							
								read_in_progress = true;
							}
						}
					}
					
					log( "Lookup queue size: " + num_ready );

					if ( next_download != null ){
																
						readRating(
							next_download,
							new CompletionListener()
							{
								@Override
								public void
								operationComplete(
									RatingResults	results )
								{
									synchronized( torrentRatings ){
										
										read_in_progress = false;
																					
										SimpleTimer.addEvent(
											"Rating:read:delay",
											SystemTime.getCurrentTime() + READ_DELAY,
											new TimerEventPerformer()
											{
												@Override
												public void
												perform(
													TimerEvent event )
												{														
													read_dispatcher.dispatch( dispatcher );
												}
											});
										
									}
								}
							});
					}
				}
			});
	}
	
	private void
	addForPublish(
		Download	download,
		boolean		is_priority )
	{
		if ( download.isRemoved()){
			
			downloadRemoved( download );
			
			return;
		}
		
		synchronized( torrentRatings ){
			
			downloads_to_publish.remove( download );
			
			if ( is_priority || downloads_to_publish.size() == 0 ){
				
				downloads_to_publish.addFirst( download );
				
			}else{
				
				downloads_to_publish.add( RandomUtils.nextInt( downloads_to_publish.size()), download );
			}
		}
		
		write_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					if ( destroyed ){
						
						return;
					}

					if ( write_dispatcher.getQueueSize() > 0 ){
						
						return;
					}
					
					Download download;
					
					synchronized( torrentRatings ){
						
						if ( write_in_progress ){
							
							return;
						}
						
						if ( downloads_to_publish.size() > 0 ){
							
							download = downloads_to_publish.removeFirst();
							
							write_in_progress = true;
							
						}else{
							
							download = null;
						}
					}
					
					log( "Publish queue size: " + downloads_to_publish.size());
					
					if ( download != null ){
												
						final AERunnable dispatcher = this;
						
						writeRating(
							download,
							new CompletionListener()
							{
								@Override
								public void
								operationComplete(
									RatingResults	results )
								{
									synchronized( torrentRatings ){
										
										write_in_progress = false;
										
										if ( downloads_to_publish.size() > 0 ){
											
											SimpleTimer.addEvent(
												"Rating:write:delay",
												SystemTime.getCurrentTime() + WRITE_DELAY,
												new TimerEventPerformer()
												{
													@Override
													public void
													perform(
														TimerEvent event )
													{														
														write_dispatcher.dispatch( dispatcher );
													}
												});
										}
									}
								}
							});
					}
				}
			});
	}
	
	public RatingResults 
	getRatingsForDownload(
		Download d )
	{
		RatingResults result = null;

		synchronized( torrentRatings ){

			result = torrentRatings.get( d );       
		}

		if ( result != null ){

			return result;
		}

		result = new RatingResults();

		try{
			String str = d.getAttribute( attributeGlobalRating );

			if ( str != null ){

				float f = Float.parseFloat(str);

				result.setAverage(f);
				
			}else{
				
				RatingData rd = loadRatingsFromDownload( d );
				
				int score = rd.getScore();
				
				if ( score > 0 ){
					
					result.setAverage( score );
				}
			}
		}catch( Throwable e ){

			Debug.out( e );
		}

		synchronized( torrentRatings ){
			
			if ( !torrentRatings.containsKey( d )){
				
				torrentRatings.put( d, result );
			}
		}
		
		return( result );
	}


	public RatingData 
	loadRatingsFromDownload(
		Download download ) 
	{
		String strRating = download.getAttribute(attributeRating);	
		String comment   = download.getAttribute(attributeComment);		

		int rating = 0;
		
		if ( strRating != null ){
			
			try{
				rating = Integer.parseInt(strRating);

			}catch( Throwable e ){

				e.printStackTrace();
			}
		}
		
		if (comment == null ){
			
			comment = "";
		}
		
		String nick = plugin.getNick();
		
		return( new RatingData( rating,nick,comment ));   
	}

	
	public void 
	storeRatingsToDownload(
		Download 	download,
		RatingData 	data ) 
	{
		RatingData oldData = loadRatingsFromDownload(download);
		
		boolean updateNeeded = false;
		
		updateNeeded |= oldData.getScore() != data.getScore();
		updateNeeded |= !oldData.getComment().equals(data.getComment());

		if (updateNeeded ){
			
			download.setAttribute(attributeRating,"" + data.getScore());
			download.setAttribute(attributeComment, data.getComment());
		}

		updateNeeded |= !oldData.getNick().equals(data.getNick());
				
		if ( updateNeeded ){
			
			addForPublish( download, true );
		}
	}

	public void 
	readRating(
		final byte[]				hash,
		final CompletionListener 	listener )
	{	
		if ( database_public == null ){
			
			listener.operationComplete( null );
			
			return;
		}
		
		List<DistributedDatabase>	ddbs = new ArrayList<DistributedDatabase>();
		
		ddbs.add( database_public );
		
		readRating( ddbs, hash, listener );
	}
	
	public void 
	readRating(
		final List<DistributedDatabase>	ddbs,
		final byte[]					hash,
		final CompletionListener 		listener )
	{	
		DistributedDatabase ddb = null;
		
		for ( DistributedDatabase x: ddbs ){
			
			if ( x.getNetwork() == AENetworkClassifier.AT_PUBLIC ){
				
				ddb = x; 
				
				break;
				
			}else{
				
				ddb = x;
			}
		}
		
		if ( ddb == null || !ddb.isAvailable()){
			
			listener.operationComplete( null );
			
			return;
		}
				
		final String hash_str =  ByteFormatter.encodeString( hash );
		
		try{
			log( hash_str + " : getting rating" );
			
			DistributedDatabaseKey ddKey = ddb.createKey(KeyGenUtils.buildRatingKey( hash ),"Ratings read: " + hash_str);
			
			boolean	is_pub = ddb.getNetwork() == AENetworkClassifier.AT_PUBLIC;
			
			int timeout = READ_BY_HASH_TIMEOUT;
			
			if ( !is_pub ){
				
				timeout *= 2;
			}
			
			ddb.read(
				new DistributedDatabaseListener() 
				{
					List<DistributedDatabaseValue> results = new ArrayList<DistributedDatabaseValue>();
	
					@Override
					public void
					event(
						DistributedDatabaseEvent event) 
					{
						if (event.getType() == DistributedDatabaseEvent.ET_VALUE_READ ){
							
							results.add(event.getValue());
							
						}else if (	event.getType() == DistributedDatabaseEvent.ET_OPERATION_COMPLETE || 
									event.getType() == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ){
							
							RatingResults ratings = new RatingResults();

							try{
								log( hash_str + " : Rating read complete - results=" + results.size());
								
		
								for ( int i = 0 ; i < results.size() ; i++ ){
		
									DistributedDatabaseValue value = results.get(i);
		
									try{
										byte[] bValue = (byte[]) value.getValue(byte[].class);
		
										RatingData data = new RatingData(bValue);
		
										log("        " + data.getScore() + ", " + value.getContact().getName() + ", " +  data.getNick() + " : " + data.getComment());
		
										ratings.addRating( data, true );
		
									}catch( Throwable e ){
		
										Debug.out( e );
									}                            
								}
								
							}finally{
							
								listener.operationComplete( ratings );
							}
						}
				}
			}, ddKey, timeout, DistributedDatabase.OP_PRIORITY_HIGH );   

		}catch( Throwable e ){

			Debug.out( e );
			
			listener.operationComplete( null );
		}
	}

	private AsyncDispatcher		chat_read_dispatcher 	= new AsyncDispatcher( "Ratings:crd" );
	private AsyncDispatcher		chat_write_dispatcher 	= new AsyncDispatcher( "Ratings:cwd" );

	
	private void 
	readRating(
		final Download 				download,
		final CompletionListener 	listener )
	{	
		List<DistributedDatabase> ddbs = plugin.getDDBs( download );
		
		if ( ddbs.size() == 0 ){
			
			listener.operationComplete( null );
			
			return;
		}
		
			// if public network is there then we always just read from that
		
		DistributedDatabase	database = ddbs.get(0);
		
		for ( int i=1;i<ddbs.size();i++){
			
			DistributedDatabase d = ddbs.get(i);
			
			if ( d.getNetwork() == AENetworkClassifier.AT_PUBLIC ){
				
				database = d;
				
				break;
			}
		}
		
		if ( BuddyPluginUtils.isBetaChatAvailable()){

			chat_read_dispatcher.dispatch(
				new AERunnable() {
					
					@Override
					public void 
					runSupport() 
					{
						if ( !enable_auto_chat ){
							
							return;
						}

						Map<String,Object> peek_data = BuddyPluginUtils.peekChat( download );
							
						if ( peek_data != null ){
							
							Number	message_count 	= (Number)peek_data.get( "m" );
							Number	node_count 		= (Number)peek_data.get( "n" );
							
							if ( message_count != null && node_count != null ){
								
								if ( message_count.intValue() > 0 ){
									
									BuddyPluginBeta.ChatInstance chat =  getChatForDownload( download );
				
									if ( chat != null ){
										
										chat.setAutoNotify( true );
									}
								}
							}	
						}
					}
				});
		}
		
		final String torrentName = download.getTorrent().getName();
		
		try{
			log( torrentName + " : getting rating" );
			
			DistributedDatabaseKey ddKey = database.createKey(KeyGenUtils.buildRatingKey(download),"Ratings read: " + torrentName);
			
			database.read(
				new DistributedDatabaseListener() 
				{
					List<DistributedDatabaseValue> results = new ArrayList<DistributedDatabaseValue>();
	
					@Override
					public void
					event(
						DistributedDatabaseEvent event) 
					{
						if (event.getType() == DistributedDatabaseEvent.ET_VALUE_READ ){
							
							results.add(event.getValue());
							
						}else if (	event.getType() == DistributedDatabaseEvent.ET_OPERATION_COMPLETE || 
									event.getType() == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ){
							
							RatingResults ratings = new RatingResults();

							try{
								log( torrentName + " : Rating read complete - results=" + results.size());
									
								if ( results.size() == 0 ){
									
										// missed our own data it seems
									
									RatingData my_data = loadRatingsFromDownload( download );
									
									if ( my_data.needPublishing()){
									
										ratings.addRating( my_data, false );
									}
									
								}else{
									for ( int i = 0 ; i < results.size() ; i++ ){
			
										DistributedDatabaseValue value = results.get(i);
			
										try{
											byte[] bValue = (byte[]) value.getValue(byte[].class);
			
											RatingData data = new RatingData(bValue);
			
											log("        " + data.getScore() + ", " + value.getContact().getName() + ", " +  data.getNick() + " : " + data.getComment());
			
											ratings.addRating( data, false );
			
										}catch( Throwable e ){
			
											Debug.out( e );
										}                            
									}
								}
								
								synchronized( torrentRatings ){
		
									torrentRatings.put( download, ratings );
		
									download.setAttribute( attributeGlobalRating, "" + ratings.getRealAverageScore());
								}			
							}finally{
							
								listener.operationComplete( ratings );
							}
						}
				}
			}, ddKey, database.getNetwork()==AENetworkClassifier.AT_PUBLIC?READ_PUBLIC_TIMEOUT:READ_NON_PUBLIC_TIMEOUT );   

		}catch( Throwable e ){

			Debug.out( e );
			
			listener.operationComplete( null );
		}
	}


	private void 
	writeRating(      
		final Download 				download,
		final CompletionListener 	_listener ) 
	{
		final List<DistributedDatabase> ddbs = plugin.getDDBs( download );

		if ( ddbs.size() == 0 ){
			
			_listener.operationComplete( null );
			
			return;
		}
		
		final RatingData data = loadRatingsFromDownload( download );
		
		int score = data.getScore();
		
			//Non scored torrent aren't published,
			//Comments require score
		
		if ( score == 0 ){
			
			_listener.operationComplete( null );
			
			return;
		}
		
		final CompletionListener listener = 
			new CompletionListener()
			{
				int	num_done = 0;
				
				@Override
				public void operationComplete(RatingResults ratings) {
				
					synchronized( this ){
						num_done++;
						
						if ( num_done < ddbs.size()){
							
							return;
						}
					}
					
					_listener.operationComplete( null );
					
					addForLookup( download, true );
				}
			};
			
		if ( BuddyPluginUtils.isBetaChatAvailable()){
			
			chat_write_dispatcher.dispatch(
					new AERunnable() {
						
						@Override
						public void 
						runSupport() 
						{
							if ( !enable_auto_chat ){
								
								return;
							}
							
							final BuddyPluginBeta.ChatInstance chat =  getChatForDownload( download );
								
							if ( chat != null ){
									
								chat.setAutoNotify( true );
								
								String msg = "Rating: " + data.getScore();
								
								String comment = data.getComment().trim();
								
								if ( comment.length() > 0 ){
									
									msg += ", " + comment;
								}
								
								final String f_msg = msg;
								
								final Runnable do_write = 
									new Runnable()
									{
										@Override
										public void
										run()
										{		
											Map<String,Object>	flags 	= new HashMap<String, Object>();
											
											flags.put( BuddyPluginBeta.FLAGS_MSG_ORIGIN_KEY, BuddyPluginBeta.FLAGS_MSG_ORIGIN_RATINGS );
											
											Map<String,Object>	options = new HashMap<String, Object>();
											
											chat.setSharedNickname( false );
											
											chat.sendMessage( f_msg, flags, options );
										}
									};
								
								String str = download.getAttribute( attributeChatState );

								boolean	 written = str != null && str.equals( "w" );
									
								if ( written ){
									
									waitForChat(
										chat, 
										new AERunnable()
										{
											@Override
											public void
											runSupport() 
											{
												List<ChatMessage>	messages = chat.getMessages();
												
												if ( messages.size() > 50 ){
													
														// busy, let's not even bother checking
													
													return;
												}
												
												for ( ChatMessage message: messages ){
													
													if ( message.getParticipant().isMe()){
														
														if ( message.getMessage().equals( f_msg )){
																														
															return;
														}
													}
												}
												
												do_write.run();
											};
										});
									
								}else{
									
									do_write.run();
									
									download.setAttribute( attributeChatState, "w" );
								}
							}
						}
					});
		}
		
		for ( DistributedDatabase database: ddbs ){
			
			try{
				final String torrentName = download.getTorrent().getName();
	
				byte[] value = data.encodes();
	
				log( torrentName + " : publishing rating" );
				
				DistributedDatabaseKey ddKey = database.createKey(KeyGenUtils.buildRatingKey(download),"Ratings write: " + torrentName);
				
				DistributedDatabaseValue ddValue = database.createValue(value);
	
				ddKey.setFlags( DistributedDatabaseKey.FL_ANON );
	
				database.write(
					new DistributedDatabaseListener() 
					{
						@Override
						public void
						event(
							DistributedDatabaseEvent event) 
						{
							if( event.getType() == DistributedDatabaseEvent.ET_OPERATION_COMPLETE) {
							
								log(torrentName + " : rating write ok");
								
								listener.operationComplete( null );
																						
							}else if( event.getType() == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ){
								
								log( torrentName + " : rating write failed" );
								
								listener.operationComplete( null );								
							}
						}
					}, ddKey, ddValue );
				
			}catch( Throwable e ){
				
				Debug.out( e );
				
				listener.operationComplete( null );
			}
		}
	} 

	private ChatInstance
	getChatForDownload(
		Download		download )
	{
		synchronized( DOWNLOAD_CHAT_KEY ) {
		
			ChatInstance chat = (ChatInstance)download.getUserData( DOWNLOAD_CHAT_KEY );
			
			if ( chat == null || chat.isDestroyed()) {
									
				chat = BuddyPluginUtils.getChat( download );
			
				download.setUserData( DOWNLOAD_CHAT_KEY, chat );
			}
			
			return( chat );
		}
	}
	
	private void
	log(
		String	str )
	{
		plugin.logInfo( str );
	}
}
