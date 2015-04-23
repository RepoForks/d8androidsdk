/*
 * The MIT License (MIT)
 *  Copyright (c) 2014 Lemberg Solutions Limited
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *   SOFTWARE.
 */

package com.ls.util.image;

import com.android.volley.VolleyError;
import com.ls.drupal.AbstractBaseDrupalEntity;
import com.ls.drupal.DrupalClient;
import com.ls.drupal.DrupalImageEntity;
import com.ls.drupal.R;
import com.ls.http.base.ResponseData;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Created on 22.04.2015.
 */
public class DrupalImageView extends ImageView {

    private static DrupalClient sharedClient;

    private static DrupalClient getSharedClient(Context context)
    {
        synchronized (DrupalImageView.class) {
            if (sharedClient == null) {
                sharedClient = new DrupalClient(null, context);
            }
        }

        return sharedClient;
    }

    /**
     * Use this method to provide default drupal client, used by all image views.
     * @param client to be used in order to load images.
     */
    public static void setupSharedClient(DrupalClient client)
    {
        synchronized (DrupalImageView.class) {
            DrupalImageView.sharedClient = client;
        }
    }

    private DrupalClient localClient;

    private ImageContainer imageContainer;

    private Drawable noImageDrawable;

    private ImageLoadingListener imageLoadingListener;

    public DrupalImageView(Context context) {
        super(context);
        initView(context,null);
    }

    public DrupalImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrupalImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context,attrs);
    }

    public void initView(Context context, AttributeSet attrs)
    {
        if (this.isInEditMode()) {
            return;
        }
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.DrupalImageView);

        Drawable noImageDrawable = array.getDrawable(R.styleable.DrupalImageView_noImageResource);
        if (noImageDrawable != null) {
            this.setNoImageDrawable(noImageDrawable);
        }

        String imagePath = array.getString(R.styleable.DrupalImageView_srcPath);
        if (!TextUtils.isEmpty(imagePath)) {
            this.setImageWithPath(imagePath);
        }
        array.recycle();
    }

    public void setImageWithPath(String imagePath)
    {
        if (this.isInEditMode()) {
            return;
        }

        DrupalClient client = this.getClient();
        if(client == null)
        {
            throw new IllegalStateException("No DrupalClient set. Please provide local or shared DrupalClient to perform loading");
        }

        if(this.imageContainer != null && this.imageContainer.url.equals(imagePath))
        {
            return;
        }

        this.cancelLoading();

        if(TextUtils.isEmpty(imagePath))
        {
            this.setImageDrawable(null);
            this.imageContainer = null;
            return;
        }

        this.imageContainer = new ImageContainer(imagePath,client);
        this.startLoading();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        cancelLoading();
        this.imageContainer = null;
        superSetImageDrawable(drawable);
    }

    /**
     * Method is calling original ImageView setDrawable method directly
     * @param drawable
     */
    protected void superSetImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
    }

    public Drawable getNoImageDrawable() {
        return noImageDrawable;
    }

    public void setNoImageDrawableResource(int resource)
    {
        this.setImageDrawable(this.getContext().getResources().getDrawable(resource));
    }

    public void setNoImageDrawable(Drawable noImageDrawable) {
        if(this.noImageDrawable != noImageDrawable) {
            if(this.getDrawable()==this.noImageDrawable)
            {
                superSetImageDrawable(null);
            }
            this.noImageDrawable = noImageDrawable;
            applyNoImageDrawableIfNeeded();
        }
    }

    public ImageLoadingListener getImageLoadingListener() {
        return imageLoadingListener;
    }

    public void setImageLoadingListener(ImageLoadingListener imageLoadingListener) {
        this.imageLoadingListener = imageLoadingListener;
    }


    public DrupalClient getLocalClient() {
        return localClient;
    }

    public void setLocalClient(DrupalClient localClient) {
        this.localClient = localClient;
    }

    public void cancelLoading()
    {
        if(this.imageContainer != null)
        {
            this.imageContainer.cancelLoad();
        }
    }

    public void startLoading()
    {
        if(this.imageContainer != null)
        {
            this.imageContainer.loadImage();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.cancelLoading();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.startLoading();
    }

    protected void applyNoImageDrawableIfNeeded()
    {
        if(this.getDrawable()==null)
        {
            superSetImageDrawable(noImageDrawable);
        }
    }

    private DrupalClient getClient()
    {
        if(this.localClient != null)
        {
            return this.localClient;
        }

        return DrupalImageView.getSharedClient(this.getContext());
    }

    private class ImageContainer
    {
        DrupalImageEntity image;
        String url;
        DrupalClient client;

        ImageContainer(String url,DrupalClient client)
        {
            this.url = url;
            this.client = client;
            DrupalImageEntity entity = new DrupalImageEntity(client);
            entity.setImagePath(url);
        }

        void cancelLoad()
        {
            image.cancellAllRequests();
        }

        void loadImage()
        {
            if(image.getManagedData() == null) {
                InternalImageLoadingListener listener = new InternalImageLoadingListener(url);
                image.pullFromServer(false, url, listener);
            }
        }
    }

    private class InternalImageLoadingListener implements AbstractBaseDrupalEntity.OnEntityRequestListener
    {
        private String acceptableURL;
        InternalImageLoadingListener(String url)
        {
            this.acceptableURL = url;
        }

        @Override
        public void onRequestCompleted(AbstractBaseDrupalEntity entity, Object tag, ResponseData data) {
            Drawable image = ((DrupalImageEntity) entity).getManagedData();
            if(checkCurrentURL())
            {
                superSetImageDrawable(image);
            }

            if(imageLoadingListener != null)
            {
                imageLoadingListener.onImageLoadingComplete(DrupalImageView.this,image);
            }
        }

        @Override
        public void onRequestFailed(AbstractBaseDrupalEntity entity, Object tag, VolleyError error) {
            applyNoImageDrawableIfNeeded();
            if(imageLoadingListener != null)
            {
                imageLoadingListener.onImageLoadingFailed(DrupalImageView.this, error);
            }
        }

        @Override
        public void onRequestCanceled(AbstractBaseDrupalEntity entity, Object tag) {
            applyNoImageDrawableIfNeeded();
            if(imageLoadingListener != null)
            {
                imageLoadingListener.onImageLoadingCancelled(DrupalImageView.this, this.acceptableURL);
            }
        }

        private boolean checkCurrentURL()
        {
            return imageContainer != null && imageContainer.url != null && imageContainer.url.equals(acceptableURL);
        }
    }

    public static interface ImageLoadingListener{
        void onImageLoadingComplete(DrupalImageView view, Drawable image);
        void onImageLoadingFailed(DrupalImageView view,VolleyError error);
        void onImageLoadingCancelled(DrupalImageView view,String path);
    }

}