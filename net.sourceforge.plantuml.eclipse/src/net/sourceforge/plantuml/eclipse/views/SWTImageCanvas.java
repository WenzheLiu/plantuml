package net.sourceforge.plantuml.eclipse.views;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Resource;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ScrollBar;

import net.sourceforge.plantuml.eclipse.Activator;
import net.sourceforge.plantuml.eclipse.utils.DefaultLinkOpener;
import net.sourceforge.plantuml.eclipse.utils.ILinkOpener;
import net.sourceforge.plantuml.eclipse.utils.LinkData;
import net.sourceforge.plantuml.eclipse.utils.PlatformLinkOpener;

/**
 * 
 */
public class SWTImageCanvas extends Canvas {
	/* zooming rates in x and y direction are equal. */
	final float ZOOMIN_RATE = 1.1f; /* zoomin rate */
	final float ZOOMOUT_RATE = 0.9f; /* zoomout rate */
	private Image sourceImage; /* original image */
	private Image screenImage; /* screen image */
	private AffineTransform transform = new AffineTransform();
	private Cursor panCursor;
	private Cursor linkCursor, unsupportedLinkCursor;

	public SWTImageCanvas(final Composite parent) {
		this(parent, SWT.NULL);
	}

	/**
	 * Constructor for ScrollableCanvas.
	 * 
	 * @param parent
	 *            the parent of this control.
	 * @param style
	 *            the style of this control.
	 */
	public SWTImageCanvas(final Composite parent, int style) {
		super(parent, style | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.NO_BACKGROUND);

		addControlListener(new ControlAdapter() { /* resize listener. */
			public void controlResized(ControlEvent event) {
				syncScrollBars();
			}
		});
		addPaintListener(new PaintListener() { /* paint listener. */
			public void paintControl(final PaintEvent event) {
				paint(event.gc);
			}
		});
		addKeyListener(new KeyListener() {
			public void keyPressed(KeyEvent e) {
				int dx = 0, dy = 0;
				switch (e.keyCode) {
				case SWT.ARROW_DOWN:
					dy = 10;
					break;
				case SWT.ARROW_UP:
					dy = -10;
					break;
				case SWT.PAGE_DOWN:
					dy = 100;
					break;
				case SWT.PAGE_UP:
					dy = -100;
					break;
				case SWT.ARROW_LEFT:
					dx = -10;
					break;
				case SWT.ARROW_RIGHT:
					dx = 10;
					break;
				case SWT.SHIFT:
					paintLinks = true;
					redraw();
					break;
				default:
					break;
				}
				if (dx != 0 || dy != 0) {
					pan(new Point(dx, dy));
				}
			}

			public void keyReleased(KeyEvent e) {
				switch (e.keyCode) {
				case SWT.SHIFT:
					paintLinks = false;
					redraw();
					break;
				default:
					break;
				}
			}
		});
		PanHandler panHandler = new PanHandler();
		addMouseListener(panHandler);
		addMouseMoveListener(panHandler);
		panCursor = new Cursor(parent.getDisplay(), SWT.CURSOR_ARROW);
		linkCursor = new Cursor(parent.getDisplay(), SWT.CURSOR_HAND);
		unsupportedLinkCursor = new Cursor(parent.getDisplay(), SWT.CURSOR_NO);
		setCursor(panCursor);
		initScrollBars();
	}

	private Point panPoint = null, panDelta = null;

	private class PanHandler implements MouseListener, MouseMoveListener {

		public void mouseDoubleClick(MouseEvent e) {
		}

		public void mouseDown(MouseEvent e) {
			LinkData link = findLink(e.x, e.y);
			if (link == null) {
				panPoint = new Point(e.x, e.y);
				panDelta = new Point(0, 0);
			}
		}

		public void mouseMove(MouseEvent e) {
			if (panPoint != null && panDelta != null) {
				panDelta.x += panPoint.x - e.x;
				panDelta.y += panPoint.y - e.y;
				panPoint.x = e.x;
				panPoint.y = e.y;
				pan(panDelta);
			} else {
				LinkData link = findLink(e.x, e.y);
				Cursor cursor = (link != null && link.href != null ? (findBestLinkOpener(link, ILinkOpener.DEFAULT_SUPPORT) != null ? linkCursor : unsupportedLinkCursor) : panCursor);
				if (cursor != getCursor()) {
					setCursor(cursor);
				}
				String toolTip = (link != null && (link.title != null || link.href != null) ? (link.title != null ? link.title : link.href) : null), oldToolTip = getToolTipText();
				if (toolTip != oldToolTip && (toolTip == null || (! toolTip.equals(oldToolTip)))) {
					setToolTipText(toolTip);
				}
			}
		}

		public void mouseUp(MouseEvent e) {
			if (panPoint != null && panDelta != null) {
				panPoint = null;
			} else {
				LinkData link = findLink(e.x, e.y);
				if (link != null && link.href != null) {
					openLink(link);
				}
			}
		}
	}

	private void dispose(Resource resource) {
		if (resource != null && !resource.isDisposed()) {
			resource.dispose();
		}
	}
	
	/**
	 * Dispose the garbage here
	 */
	public void dispose() {
		dispose(sourceImage);
		dispose(screenImage);
		dispose(panCursor);
		dispose(linkCursor);
		dispose(unsupportedLinkCursor);
	}

	/* Paint function */
	private void paint(GC gc) {
		Rectangle clientRect = getClientArea(); // Canvas' painting area
		if (sourceImage != null) {
			Rectangle imageRect = inverseTransformRect(transform, clientRect);
			int gap = 2; // find a better start point to render
			imageRect.x -= gap;
			imageRect.y -= gap;
			imageRect.width += 2 * gap;
			imageRect.height += 2 * gap;

			Rectangle imageBound = sourceImage.getBounds();
			imageRect = imageRect.intersection(imageBound);
			Rectangle destRect = transformRect(transform, imageRect);
			if (screenImage != null) {
				screenImage.dispose();
			}
			screenImage = new Image(getDisplay(), clientRect.width, clientRect.height);
			GC newGC = new GC(screenImage);
			newGC.setClipping(clientRect);
			newGC.drawImage(sourceImage, imageRect.x, imageRect.y, imageRect.width, imageRect.height, destRect.x,
					destRect.y, destRect.width, destRect.height);
			newGC.dispose();
			gc.drawImage(screenImage, 0, 0);
			if (paintLinks) {
				paintLinks(gc);
			}
		} else {
			gc.setClipping(clientRect);
			gc.fillRectangle(clientRect);
			initScrollBars();
		}
	}

	private LinkData findLink(int canvasX, int canvasY) {
		if (links != null && links.size() > 0) {
			Point pt = null;
			for (LinkData link : links) {
				if (link.rect != null) {
					if (pt == null) {
//						pt = new Point(canvasX, canvasY);
						pt = inverseTransformPoint(transform, new Point(canvasX, canvasY));
					}
//					Rectangle destRect = transformRect(transform, link.rect);
					Rectangle destRect = link.rect;
					if (destRect.contains(pt)) {
						return link;
					}
				}
			}
		}
		return null;
	}
	
	// should provide extension point
	private Collection<ILinkOpener> linkOpeners = new ArrayList<ILinkOpener>(Arrays.asList(Activator.getDefault().getLinkOpeners()));
	
	public void addLinkOpener(ILinkOpener linkOpener) {
		linkOpeners.add(linkOpener);
	}

	public void removeLinkOpener(ILinkOpener linkOpener) {
		linkOpeners.remove(linkOpener);
	}

	public ILinkOpener findBestLinkOpener(LinkData link, int minSupport) {
		int bestSupport = ILinkOpener.NO_SUPPORT;
		ILinkOpener best = null;
		for (ILinkOpener linkOpener : linkOpeners) {
			int support = ILinkOpener.NO_SUPPORT;
			try {
				support = linkOpener.supportsLink(link);
			} catch (Exception e) {
			}
			if (support >= bestSupport) {
				bestSupport = support;
				best = linkOpener;
			}
		}
		return (bestSupport >= minSupport ? best : null);
	}
	
	public void openLink(LinkData link) {
		ILinkOpener best = findBestLinkOpener(link, ILinkOpener.DEFAULT_SUPPORT);
		if (best != null) {
			best.openLink(link);
		}
	}

	private boolean paintLinks = false;
	
	private void paintLinks(GC gc) {
		if (links != null) {
			for (LinkData link : links) {
				if (link.rect != null) {
					Rectangle destRect = transformRect(transform, link.rect);
					gc.drawRectangle(destRect);
				}
			}
		}
	}

	/* Initalize the scrollbar and register listeners. */
	private void initScrollBars() {
		ScrollBar horizontal = getHorizontalBar();
		horizontal.setEnabled(false);
		horizontal.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				scrollHorizontally((ScrollBar) event.widget);
			}
		});
		ScrollBar vertical = getVerticalBar();
		vertical.setEnabled(false);
		vertical.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				scrollVertically((ScrollBar) event.widget);
			}
		});
	}

	/* Scroll horizontally */
	private void scrollHorizontally(ScrollBar scrollBar) {
		if (sourceImage == null)
			return;

		AffineTransform af = transform;
		double tx = af.getTranslateX();
		double select = -scrollBar.getSelection();
		af.preConcatenate(AffineTransform.getTranslateInstance(select - tx, 0));
		transform = af;
		syncScrollBars();
	}

	/* Scroll vertically */
	private void scrollVertically(ScrollBar scrollBar) {
		if (sourceImage == null)
			return;

		AffineTransform af = transform;
		double ty = af.getTranslateY();
		double select = -scrollBar.getSelection();
		af.preConcatenate(AffineTransform.getTranslateInstance(0, select - ty));
		transform = af;
		syncScrollBars();
	}

	/**
	 * Synchronize the scrollbar with the image. If the transform is out of
	 * range, it will correct it. This function considers only following factors
	 * :<b> transform, image size, client area</b>.
	 */
	public void syncScrollBars() {
		if (sourceImage == null) {
			redraw();
			return;
		}

		AffineTransform af = transform;
		double sx = af.getScaleX(), sy = af.getScaleY();
		double tx = af.getTranslateX(), ty = af.getTranslateY();
		if (tx > 0)
			tx = 0;
		if (ty > 0)
			ty = 0;

		ScrollBar horizontal = getHorizontalBar();
		horizontal.setIncrement((int) (getClientArea().width / 20));
		horizontal.setPageIncrement(getClientArea().width);
		Rectangle imageBound = sourceImage.getBounds();
		int cw = getClientArea().width, ch = getClientArea().height;
		if (imageBound.width * sx > cw) { /* image is wider than client area */
			horizontal.setMaximum((int) (imageBound.width * sx));
			horizontal.setEnabled(true);
			if (((int) -tx) > horizontal.getMaximum() - cw)
				tx = -horizontal.getMaximum() + cw;
		} else { /* image is narrower than client area */
			horizontal.setEnabled(false);
			tx = (cw - imageBound.width * sx) / 2; // center if too small.
		}
		horizontal.setSelection((int) (-tx));
		horizontal.setThumb((int) (getClientArea().width));

		ScrollBar vertical = getVerticalBar();
		vertical.setIncrement((int) (getClientArea().height / 20));
		vertical.setPageIncrement((int) (getClientArea().height));
		if (imageBound.height
				* sy > ch) { /* image is higher than client area */
			vertical.setMaximum((int) (imageBound.height * sy));
			vertical.setEnabled(true);
			if (((int) -ty) > vertical.getMaximum() - ch)
				ty = -vertical.getMaximum() + ch;
		} else { /* image is less higher than client area */
			vertical.setEnabled(false);
			ty = (ch - imageBound.height * sy) / 2; // center if too small.
		}
		vertical.setSelection((int) (-ty));
		vertical.setThumb((int) (getClientArea().height));

		/* update transform. */
		af = AffineTransform.getScaleInstance(sx, sy);
		af.preConcatenate(AffineTransform.getTranslateInstance(tx, ty));
		transform = af;

		redraw();
	}

	/**
	 * Reload image from a file
	 * 
	 * @param filename
	 *            image file
	 * @return swt image created from image file
	 */
	public void loadImage(ImageData imageData) {
		if (sourceImage != null && !sourceImage.isDisposed()) {
			sourceImage.dispose();
			sourceImage = null;
		}
		sourceImage = new Image(getDisplay(), imageData);
		showOriginal();
	}
	
	private Collection<LinkData> links;
	
	public void setLinks(Collection<LinkData> links) {
		this.links = links;
	}

	public void showErrorMessage(Throwable t) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PrintWriter pw = new PrintWriter(os);
		t.printStackTrace(pw);
		pw.close();
		showErrorMessage(os.toString());
	}

	public void showErrorMessage(String s) {
		if (sourceImage != null && !sourceImage.isDisposed()) {
			sourceImage.dispose();
			sourceImage = null;
		}
		sourceImage = new Image(getDisplay(), 500, 500);
		GC gc = new GC(sourceImage);
		String[] ss = s.split("\n");
		int lineHeight = gc.getFontMetrics().getHeight();
		for (int i = 0; i < ss.length; i++) {
			String line = ss[i];
			if (line.startsWith("\t")) {
				line = "    " + line.substring(1);
				gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_BLACK));
			} else {
				gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_RED));
			}
			gc.drawString(line, 10, i * lineHeight);
		}
		gc.dispose();
		showOriginal();
	}

	/**
	 * Reset the image data and update the image
	 * 
	 * @param data
	 *            image data to be set
	 */
	public void setImageData(ImageData data) {
		if (sourceImage != null)
			sourceImage.dispose();
		if (data != null)
			sourceImage = new Image(getDisplay(), data);
		syncScrollBars();
	}

	/**
	 * Fit the image onto the canvas
	 */
	public void fitCanvas() {
		if (sourceImage == null)
			return;
		Rectangle imageBound = sourceImage.getBounds();
		Rectangle destRect = getClientArea();
		double sx = (double) destRect.width / (double) imageBound.width;
		double sy = (double) destRect.height / (double) imageBound.height;
		double s = Math.min(sx, sy);
		double dx = 0.5 * destRect.width;
		double dy = 0.5 * destRect.height;
		centerZoom(dx, dy, s, new AffineTransform());
	}

	/**
	 * Show the image with the original size
	 */
	public void showOriginal() {
		if (sourceImage != null) {
			transform = new AffineTransform();
			syncScrollBars();
		}
	}

	/**
	 * Perform a zooming operation centered on the given point (dx, dy) and
	 * using the given scale factor. The given AffineTransform instance is
	 * preconcatenated.
	 * 
	 * @param dx
	 *            center x
	 * @param dy
	 *            center y
	 * @param scale
	 *            zoom rate
	 * @param af
	 *            original affinetransform
	 */
	public void centerZoom(double dx, double dy, double scale, AffineTransform af) {
		af.preConcatenate(AffineTransform.getTranslateInstance(-dx, -dy));
		af.preConcatenate(AffineTransform.getScaleInstance(scale, scale));
		af.preConcatenate(AffineTransform.getTranslateInstance(dx, dy));
		transform = af;
		syncScrollBars();
	}

	/**
	 * Zoom in around the center of client Area.
	 */
	public void zoomIn() {
		if (sourceImage == null)
			return;
		Rectangle rect = getClientArea();
		int w = rect.width, h = rect.height;
		double dx = ((double) w) / 2;
		double dy = ((double) h) / 2;
		centerZoom(dx, dy, ZOOMIN_RATE, transform);
	}

	/**
	 * Zoom out around the center of client Area.
	 */
	public void zoomOut() {
		if (sourceImage == null)
			return;
		Rectangle rect = getClientArea();
		int w = rect.width, h = rect.height;
		double dx = ((double) w) / 2;
		double dy = ((double) h) / 2;
		centerZoom(dx, dy, ZOOMOUT_RATE, transform);
	}

	/**
	 * Adjusts the scrollbars to give the effect of panning
	 * 
	 * @param dx
	 *            the relative adjustment in x-direction
	 * @param dy
	 *            the relative adjustment in y-direction
	 */
	private void pan(Point p) {
		Rectangle bounds = screenImage.getBounds();
		ScrollBar hBar = getHorizontalBar(), vBar = getVerticalBar();
		Rectangle barBounds = bounds; // new Rectangle(hBar.getMinimum(),
										// vBar.getMinimum(), hBar.getMaximum()
										// - hBar.getMinimum(),
										// vBar.getMaximum() -
										// vBar.getMinimum());

		int sdx = p.x * barBounds.width / bounds.width;
		int sdy = p.y * barBounds.height / bounds.height;
		// report back the remaining delta
		p.x = p.x - sdx * bounds.width / barBounds.width;
		p.y = p.y - sdy * bounds.height / barBounds.height;

		if (sdx != 0) {
			hBar.setSelection(hBar.getSelection() + sdx);
			scrollHorizontally(hBar);
		}
		if (sdy != 0) {
			vBar.setSelection(vBar.getSelection() + sdy);
			scrollVertically(vBar);
		}
	}

	// helper methods

	/**
	 * Given an arbitrary rectangle, get the rectangle with the given transform.
	 * The result rectangle is positive width and positive height.
	 * 
	 * @param af
	 *            AffineTransform
	 * @param src
	 *            source rectangle
	 * @return rectangle after transform with positive width and height
	 */
	private static Rectangle transformRect(AffineTransform af, Rectangle src) {
		Rectangle dest = new Rectangle(0, 0, 0, 0);
		src = absRect(src);
		Point p1 = new Point(src.x, src.y);
		p1 = transformPoint(af, p1);
		dest.x = p1.x;
		dest.y = p1.y;
		dest.width = (int) (src.width * af.getScaleX());
		dest.height = (int) (src.height * af.getScaleY());
		return dest;
	}

	/**
	 * Given an arbitrary rectangle, get the rectangle with the inverse given
	 * transform. The result rectangle is positive width and positive height.
	 * 
	 * @param af
	 *            AffineTransform
	 * @param src
	 *            source rectangle
	 * @return rectangle after transform with positive width and height
	 */
	private static Rectangle inverseTransformRect(AffineTransform af, Rectangle src) {
		Rectangle dest = new Rectangle(0, 0, 0, 0);
		src = absRect(src);
		Point p1 = new Point(src.x, src.y);
		p1 = inverseTransformPoint(af, p1);
		dest.x = p1.x;
		dest.y = p1.y;
		dest.width = (int) (src.width / af.getScaleX());
		dest.height = (int) (src.height / af.getScaleY());
		return dest;
	}

	/**
	 * Given an arbitrary point, get the point with the given transform.
	 * 
	 * @param af
	 *            affine transform
	 * @param pt
	 *            point to be transformed
	 * @return point after tranform
	 */
	private static Point transformPoint(AffineTransform af, Point pt) {
		Point2D src = new Point2D.Float(pt.x, pt.y);
		Point2D dest = af.transform(src, null);
		Point point = new Point((int) Math.floor(dest.getX()), (int) Math.floor(dest.getY()));
		return point;
	}

	/**
	 * Given an arbitrary point, get the point with the inverse given transform.
	 * 
	 * @param af
	 *            AffineTransform
	 * @param pt
	 *            source point
	 * @return point after transform
	 */
	private static Point inverseTransformPoint(AffineTransform af, Point pt) {
		Point2D src = new Point2D.Float(pt.x, pt.y);
		try {
			Point2D dest = af.inverseTransform(src, null);
			return new Point((int) Math.floor(dest.getX()), (int) Math.floor(dest.getY()));
		} catch (Exception e) {
			e.printStackTrace();
			return new Point(0, 0);
		}
	}

	/**
	 * Given arbitrary rectangle, return a rectangle with upper-left start and
	 * positive width and height.
	 * 
	 * @param src
	 *            source rectangle
	 * @return result rectangle with positive width and height
	 */
	private static Rectangle absRect(Rectangle src) {
		Rectangle dest = new Rectangle(0, 0, 0, 0);
		if (src.width < 0) {
			dest.x = src.x + src.width + 1;
			dest.width = -src.width;
		} else {
			dest.x = src.x;
			dest.width = src.width;
		}
		if (src.height < 0) {
			dest.y = src.y + src.height + 1;
			dest.height = -src.height;
		} else {
			dest.y = src.y;
			dest.height = src.height;
		}
		return dest;
	}
}
