/**
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

import java.io.InputStream;
import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;

/**
 * @author TuxPaper
 * @created Jan 10, 2006
 *
 */
public class RatingImageUtil {
	private static final String resPath = "com/aelitis/azureus/plugins/rating/ui/icons/";

	public static Image imgUnrated;

	private static Image imgRated;

	public static int starWidth;

	public static void init(Display display) {
		imgUnrated = loadImage(display, resPath + "unrated.png");
		imgRated = loadImage(display, resPath + "rated.png");

		if (imgUnrated == null)
			starWidth = 1;
		else
			starWidth = imgUnrated.getBounds().width / 5;
	}

	public static Image createRatingImage(float rating, Display display) {
		if (imgRated == null || imgUnrated == null)
			return null;

		if (rating < 0)
			rating = 0;
		else if (rating > 5)
			rating = 5;

		Image img = null;

		try {
			Rectangle bounds = imgRated.getBounds();
			int width = (int) (rating / 5.0 * bounds.width);
			if (width > 0) {
				// Create the image by making copy of the unrated, then drawing the
				// rated image on top for the specified rating.  We can't use a GC and
				// drawImage, because it doesn't handle drawing images with trasparency
				// to an image that has transparency.

				ImageData imageData = imgUnrated.getImageData();
				ImageData ratedData = imgRated.getImageData();

				for (int y = 0; y < bounds.height; y++) {
					byte[] alphas = new byte[width];
					ratedData.getAlphas(0, y, width, alphas, 0);

					int[] pixels = new int[width];
					ratedData.getPixels(0, y, width, pixels, 0);

					imageData.setPixels(0, y, width, pixels, 0);
					imageData.setAlphas(0, y, width, alphas, 0);
				}

				img = new Image(display, imageData);
			} else {

				img = new Image(display, imgUnrated, SWT.IMAGE_COPY);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return img;
	}

	public static Image createStarLineImage(float ratingStar, float ratingLine,
			Display display) {

		if (imgRated == null || imgUnrated == null)
			return null;

		if (ratingStar < 0)
			ratingStar = 0;
		else if (ratingStar > 5)
			ratingStar = 5;

		if (ratingLine < 0)
			ratingLine = 0;
		else if (ratingLine > 5)
			ratingLine = 5;

		Image img = null;

		try {
			Rectangle bounds = imgRated.getBounds();
			int widthStar = (int) (ratingStar / 5.0 * bounds.width);
			int widthLine = (int) (ratingLine / 5.0 * bounds.width);
			// Create the image by making copy of the unrated, then drawing the
			// rated image on top for the specified rating.  We can't use a GC and
			// drawImage, because it doesn't handle drawing images with trasparency
			// to an image that has transparency.
			ImageData imageData = imgUnrated.getImageData();

			if (widthStar > 0 || widthLine > 0) {
				ImageData ratedData = imgRated.getImageData();

				if (widthStar > 0)
					for (int y = 0; y < bounds.height; y++) {
						byte[] alphas = new byte[widthStar];
						ratedData.getAlphas(0, y, widthStar, alphas, 0);

						int[] pixels = new int[widthStar];
						ratedData.getPixels(0, y, widthStar, pixels, 0);

						imageData.setPixels(0, y, widthStar, pixels, 0);
						imageData.setAlphas(0, y, widthStar, alphas, 0);
					}

				if (widthLine > 0) {
					byte[] alphas = new byte[widthLine];
					Arrays.fill(alphas, (byte) 0x90);

					int[] pixels = new int[widthLine];
					Arrays.fill(pixels, (int) 0x0020FF);

					imageData.setAlphas(0, bounds.height - 2, widthLine, alphas, 0);
					imageData.setPixels(0, bounds.height - 2, widthLine, pixels, 0);

					Arrays.fill(alphas, (byte) 0x40);
					Arrays.fill(pixels, (int) 0x00107F);
					imageData.setAlphas(0, bounds.height - 1, widthLine, alphas, 0);
					imageData.setPixels(0, bounds.height - 1, widthLine, pixels, 0);
				}
			}
			img = new Image(display, imageData);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return img;
	}

	public static Image createSplitStarImage(float ratingTop, float ratingBottom,
			Display display) {

		if (imgRated == null || imgUnrated == null)
			return null;

		if (ratingTop < 0)
			ratingTop = 0;
		else if (ratingTop > 5)
			ratingTop = 5;

		if (ratingBottom < 0)
			ratingBottom = 0;
		else if (ratingBottom > 5)
			ratingBottom = 5;

		Image img = null;

		try {
			Rectangle bounds = imgRated.getBounds();
			int widthTop = (int) (ratingTop / 5.0 * bounds.width);
			int widthBottom = (int) (ratingBottom / 5.0 * bounds.width);
			// Create the image by making copy of the unrated, then drawing the
			// rated image on top for the specified rating.  We can't use a GC and
			// drawImage, because it doesn't handle drawing images with trasparency
			// to an image that has transparency.
			ImageData imageData = imgUnrated.getImageData();

			if (widthTop > 0 || widthBottom > 0) {
				ImageData ratedData = imgRated.getImageData();

				if (widthTop > 0)
					for (int y = 0; y < bounds.height / 2; y++) {
						byte[] alphas = new byte[widthTop];
						ratedData.getAlphas(0, y, widthTop, alphas, 0);

						int[] pixels = new int[widthTop];
						ratedData.getPixels(0, y, widthTop, pixels, 0);

						imageData.setPixels(0, y, widthTop, pixels, 0);
						imageData.setAlphas(0, y, widthTop, alphas, 0);
					}

				if (widthBottom > 0)
					for (int y = bounds.height / 2 + 1; y < bounds.height; y++) {
						byte[] alphas = new byte[widthBottom];
						ratedData.getAlphas(0, y, widthBottom, alphas, 0);

						int[] pixels = new int[widthBottom];
						ratedData.getPixels(0, y, widthBottom, pixels, 0);

						imageData.setPixels(0, y, widthBottom, pixels, 0);
						imageData.setAlphas(0, y, widthBottom, alphas, 0);
					}
			}

			img = new Image(display, imageData);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return img;
	}

	private static Image loadImage(Display display, String res) {
		try {
			InputStream is = RatingImageUtil.class.getClassLoader()
					.getResourceAsStream(res);
			if (is != null) {
				ImageData imageData = new ImageData(is);
				return new Image(display, imageData);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void destroy() {
		if (imgUnrated != null) {
			imgUnrated.dispose();
			imgUnrated = null;
		}
		if (imgRated != null) {
			imgRated.dispose();
			imgRated = null;
		}
	}
}
