/*
 * Created on Jul 31, 2009
 * Created by Paul Gardner
 * 
 * Copyright 2009 Vuze, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.core.devices.impl;

import java.io.*;
import java.util.*;

import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AsyncDispatcher;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.devices.Device;
import com.aelitis.azureus.core.devices.DeviceMediaRenderer;
import com.aelitis.azureus.core.devices.DeviceTemplate;
import com.aelitis.azureus.core.drivedetector.DriveDetectedInfo;
import com.aelitis.azureus.core.drivedetector.DriveDetectedListener;
import com.aelitis.azureus.core.drivedetector.DriveDetectorFactory;
import com.aelitis.azureus.util.MapUtils;

public class 
DeviceDriveManager 
	implements DriveDetectedListener
{
	private DeviceManagerImpl		manager;
	
	private Map<String,DeviceMediaRendererManual>	device_map = new HashMap<String, DeviceMediaRendererManual>();
	
	private AsyncDispatcher	async_dispatcher = new AsyncDispatcher();
	
	private boolean	listener_added;
	
	protected
	DeviceDriveManager(
		DeviceManagerImpl		_manager )
	{
		manager = _manager;
		
		if ( manager.getAutoSearch()){
			
			listener_added = true;
			
			DriveDetectorFactory.getDeviceDetector().addListener( this );
		}
	}
	
	protected void
	search()
	{
		async_dispatcher.dispatch(
			new AERunnable()
			{
				public void
				runSupport()
				{
					if ( listener_added ){
						
						return;
					}
					
					try{
							// this should synchronously first any discovered drives
						
						DriveDetectorFactory.getDeviceDetector().addListener( DeviceDriveManager.this );

					}finally{
						
						DriveDetectorFactory.getDeviceDetector().removeListener( DeviceDriveManager.this );
					}
				}
			});
	}
	
	public void 
	driveDetected(
		final DriveDetectedInfo info )
 {
		//System.out.println("DD " + info.getLocation() + " via " + Debug.getCompressedStackTrace());
		async_dispatcher.dispatch(new AERunnable() {
			public void runSupport() {
				
				Map<String, Object> infoMap = info.getInfoMap();

				boolean isWritableUSB = MapUtils.getMapBoolean(infoMap, "isWritableUSB", false);
				
				File root = info.getLocation();

				String sProdID = MapUtils.getMapString(infoMap, "ProductID",
						MapUtils.getMapString(infoMap, "Product Name", "")).trim();
				String sVendor = MapUtils.getMapString(infoMap, "VendorID",
						MapUtils.getMapString(infoMap, "Vendor Name", "")).trim();

				if (sProdID.toLowerCase().contains("android")
						|| sVendor.toLowerCase().contains("motorola")
						|| sVendor.equalsIgnoreCase("samsung")) {
					
					if (isWritableUSB && sVendor.equalsIgnoreCase("samsung")) {
						// Samsungs that start with Y are MP3 players
						// Samsungs that don't have a dash aren't smart phones (none that we know of anyway..)
						// Fake not writable so we remove the device instead of adding it
						isWritableUSB = !sProdID.startsWith("Y")
								&& sProdID.matches(".*[A-Z]-.*");
					}

					boolean hidden = false;
					String name = sVendor;
					if (name.length() > 0) {
						name += " ";
					}
					name += sProdID;
					if (sVendor.compareToIgnoreCase("motorola") == 0) {
						if (sProdID.equalsIgnoreCase("a855")) {
							name = "Droid";
						} else if (sProdID.equalsIgnoreCase("a955")) {
							name = "Droid 2";
						} else if (sProdID.equalsIgnoreCase("mb810")) {
							name = "Droid X";
						}
					} else if (sProdID.equalsIgnoreCase("sgh-t959")) {
						name = "Samsung Vibrant"; // non-card
					} else if (sProdID.toLowerCase().contains("sgh-t959")) {
						hidden = true;
					}

					String id = "android.";
					id += sProdID.replaceAll(" ", ".").toLowerCase();
					if (sVendor.length() > 0) {
						id += "." + sVendor.replaceAll(" ", ".").toLowerCase();
					}
					
					if (isWritableUSB) {
						addDevice(name, id, root, new File(root, "videos"), hidden);
					} else {
						//Fixup old bug where we were adding Samsung hard drives as devices
						Device existingDevice = getDeviceMediaRendererByClassification(id);
						if (existingDevice != null) {
							existingDevice.remove();
						}
					}
					return;
				} else if (isWritableUSB && sVendor.toLowerCase().equals("rim")
						&& !sProdID.toLowerCase().contains(" SD")) {
					// for some reason, the SD card never fully attaches, only the main device drive
					String name = sVendor;
  				if (name.length() > 0) {
  					name += " ";
  				}
  				name += sProdID;
					String id = "";
					id += sProdID.replaceAll(" ", ".").toLowerCase();
					if (sVendor.length() > 0) {
						id += "." + sVendor.replaceAll(" ", ".").toLowerCase();
					}
					addDevice(name, id, root, new File(root, "videos"), false);
					return;
				}

				if (isWritableUSB && root.exists()) {

					File[] folders = root.listFiles();

					if (folders != null) {

						Set<String> names = new HashSet<String>();

						for (File file : folders) {

							names.add(file.getName().toLowerCase());
						}

						if (names.contains("psp") && names.contains("video")) {
							addDevice("PSP", "sony.PSP", root, new File(root, "VIDEO"), false);
						}
					}
				}
			}
		});
	}
	
	protected DeviceMediaRenderer getDeviceMediaRendererByClassification(String target_classification) {
		DeviceImpl[] devices = manager.getDevices();
		
		for ( DeviceImpl device: devices ){
			
			if ( device instanceof DeviceMediaRenderer ){
			
				DeviceMediaRenderer renderer = (DeviceMediaRenderer)device;
				
				String classification = renderer.getClassification();
			
				if ( classification.equalsIgnoreCase( target_classification )){
																
					return renderer;
				}
			}
		}

		return null;
	}

	protected void addDevice(
			String target_name, 
			String target_classification,
			File root,
			File target_directory,
			boolean hidden)
	{
		
		DeviceMediaRenderer existingDevice = getDeviceMediaRendererByClassification(target_classification);
		if (existingDevice instanceof DeviceMediaRendererManual ) {
			mapDevice( (DeviceMediaRendererManual) existingDevice, root, target_directory );
			
			return;
		}
		
		DeviceTemplate[] templates = manager.getDeviceTemplates( Device.DT_MEDIA_RENDERER );
		
		DeviceMediaRendererManual	renderer = null;
		
		for ( DeviceTemplate template: templates ){
			
			if ( template.getClassification().equalsIgnoreCase( target_classification )){
				
				try{
					renderer = (DeviceMediaRendererManual)template.createInstance( target_name );

					break;
					
				}catch( Throwable e ){
					
					log( "Failed to add device", e );
				}
			}
		}
		
		if ( renderer == null ){
			
				// damn, the above doesn't work until devices is turned on...
			
			try{
				renderer = (DeviceMediaRendererManual)manager.createDevice( Device.DT_MEDIA_RENDERER, null, target_classification, target_name );
				
			}catch( Throwable e ){
				
				log( "Failed to add device", e );
			}
		}
		
		if ( renderer != null ){
			
			try{
				renderer.setAutoCopyToFolder( true );
				renderer.setHidden(hidden);
				
				mapDevice( renderer, root, target_directory );
				
				return;
				
			}catch( Throwable e ){
				
				log( "Failed to add device", e );
			}
		}
		}

	public void 
	driveRemoved(
		final DriveDetectedInfo info )
	{
		async_dispatcher.dispatch(
			new AERunnable()
			{
				public void
				runSupport()
				{
					unMapDevice( info.getLocation());
				}
			});
	}
	
	protected void
	mapDevice(
		DeviceMediaRendererManual		renderer,
		File							root,
		File							copy_to )
	{
		DeviceMediaRendererManual	existing;
		
		synchronized( device_map ){
			
			existing = device_map.put( root.getAbsolutePath(), renderer );
		}
		
		if ( existing != null && existing != renderer ){
			
			log( "Unmapped " + existing.getName() + " from " + root );
			
			existing.setCopyToFolder( null );
		}
		
		log( "Mapped " + renderer.getName() + " to " + root );

		renderer.setCopyToFolder( copy_to );
		
		renderer.setLivenessDetectable( true );
		
		renderer.alive();
	}
	
	protected void
	unMapDevice(
		File							root )
	{
		DeviceMediaRendererManual existing;
		
		synchronized( device_map ){
			
			existing = device_map.remove( root.getAbsolutePath());
		}
		
		if ( existing != null ){
			
			log( "Unmapped " + existing.getName() + " from " + root );

			existing.setCopyToFolder( null );
			
			existing.dead();
		}
	}
	
	protected void
	log(
		String str )
	{
		manager.log( "DriveMan: " + str );
	}
	
	protected void
	log(
		String 		str,
		Throwable 	e )
	{
		manager.log( "DriveMan: " + str, e );
	}
}
