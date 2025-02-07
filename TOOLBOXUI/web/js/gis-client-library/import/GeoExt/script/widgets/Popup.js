/**
 * Copyright (c) 2008-2009 The Open Source Geospatial Foundation
 * 
 * Published under the BSD license.
 * See http://svn.geoext.org/core/trunk/geoext/license.txt for the full text
 * of the license.
 */

/** api: (define)
 *  module = GeoExt
 *  class = Popup
 *  base_link = `Ext.Window <http://extjs.com/deploy/dev/docs/?class=Ext.Window>`_
 */
Ext.namespace("GeoExt");

/** api: example
 *  Sample code to create a popup anchored to a feature:
 * 
 *  .. code-block:: javascript
 *     
 *      var popup = new GeoExt.Popup({
 *          title: "My Popup",
 *          feature: feature,
 *          width: 200,
 *          html: "<div>Popup content</div>",
 *          collapsible: true
 *      });
 */

/** api: constructor
 *  .. class:: Popup(config)
 *   
 *      Popups are a specialized Window that supports anchoring
 *      to a particular feature in a MapPanel.  When a popup
 *      is anchored to a feature, that means that the popup
 *      will visibly point to the feature on the map, and move
 *      accordingly when the map is panned or zoomed.
 */
GeoExt.Popup = Ext.extend(Ext.Window, {

    /** api: config[anchored]
     *  ``Boolean``  The popup begins anchored to its feature.  Default is
     *  ``true``.
     */
    anchored: true,

    /** api: config[map]
     *  ``OpenLayers.Map`` or :class:`GeoExt.MapPanel`
     *  The map this popup will be anchored to (only required if ``anchored``
     *  is set to true and the map cannot be derived from the ``feature``'s
     *  layer.
     */
    map: null,

    /** api: config[panIn]
     *  ``Boolean`` The popup should pan the map so that the popup is
     *  fully in view when it is rendered.  Default is ``true``.
     */
    panIn: true,

    /** api: config[unpinnable]
     *  ``Boolean`` The popup should have a "unpin" tool that unanchors it from
     *  its feature.  Default is ``true``.
     */
    unpinnable: true,

    /** api: config[feature]
     *  ``OpenLayers.Feature`` A location for this popup's anchor.  One of
     *  ``feature`` or ``lonlat`` must be provided.
     */
    feature: null,

    /** api: config[lonlat]
     *  ``OpenLayers.LonLat`` A location for this popup's anchor.  One of
     *  ``feature`` or ``lonlat`` must be provided.
     */
    lonlat: null,


    /**
     * Some Ext.Window defaults need to be overriden here
     * because some Ext.Window behavior is not currently supported.
     */    

    /** private: config[animCollapse]
     *  ``Boolean`` Animate the transition when the panel is collapsed.
     *  Default is ``false``.  Collapsing animation is not supported yet for
     *  popups.
     */
    animCollapse: false,

    /** private: config[draggable]
     *  ``Boolean`` Enable dragging of this Panel.  Defaults to ``false``
     *  because the popup defaults to being anchored, and anchored popups
     *  should not be draggable.
     */
    draggable: false,

    /** private: config[shadow]
     *  ``Boolean`` Give the popup window a shadow.  Defaults to ``false``
     *  because shadows are not supported yet for popups (the shadow does
     *  not look good with the anchor).
     */
    shadow: false,

    /** api: config[popupCls]
     *  ``String`` CSS class name for the popup DOM elements.  Default is
     *  "gx-popup".
     */
    popupCls: "gx-popup",

    /** api: config[ancCls
     *  ``String``  CSS class name for the popup's anchor.
     */
    ancCls: null,

    /** private: method[initComponent]
     *  Initializes the popup.
     */
    initComponent: function() {
       /* if(this.map instanceof GeoExt.MapPanel) {
            this.map = this.map.map;
        }*/
        if(!this.map && this.feature && this.feature.layer) {
            this.map = this.feature.layer.map;
        }
        if (!this.feature && this.lonlat) {
            this.feature = new OpenLayers.Feature.Vector(new OpenLayers.Geometry.Point(this.lonlat.lon, this.lonlat.lat));
        }
        if(this.anchored) {
            this.addAnchorEvents();
        }

        this.baseCls = this.popupCls + " " + this.baseCls;

        this.elements += ',anc';

        

        GeoExt.Popup.superclass.initComponent.call(this);
    },

    /** private: method[onRender]
     *  Executes when the popup is rendered.
     */
    onRender: function(ct, position) {
        GeoExt.Popup.superclass.onRender.call(this, ct, position);
        this.ancCls = this.popupCls + "-anc";

        //create anchor dom element.
        this.createElement("anc", this.el);
    },

    /** private: method[initTools]
     *  Initializes the tools on the popup.  In particular,
     *  it adds the 'unpin' tool if the popup is unpinnable.
     */
    initTools : function() {
        if(this.unpinnable) {
            this.addTool({
                id: 'unpin',
                handler: this.unanchorPopup.createDelegate(this, [])
            });
        }

        GeoExt.Popup.superclass.initTools.call(this);
        this.tools.maximize.hide();
    },

    /** private: method[show]
     *  Override.
     */
    show: function() {
        GeoExt.Popup.superclass.show.apply(this, arguments);
        if(this.anchored) {
            this.position();
            if(this.panIn && !this._mapMove) {
                this.panIntoView();
            }
        }
    },
    
    /** api: method[setSize]
     *  :param w: ``Integer``
     *  :param h: ``Integer``
     *  
     *  Sets the size of the popup, taking into account the size of the anchor.
     */
    setSize: function(w, h) {
        if(this.anc) {
            var ancSize = this.getAnchorElement().getSize();
            if(typeof w == 'object') {
                h = w.height - ancSize.height;
                w = w.width;
            } else if(!isNaN(h)){
                h = h - ancSize.height;
            }
        }
        GeoExt.Popup.superclass.setSize.call(this, w, h);
    },

    /** private: method[position]
     *  Positions the popup relative to its feature
     */
    position: function() {
        var centerLonLat = this.feature.geometry.getBounds().getCenterLonLat();

        if(this._mapMove === true) {
            var visible = this.map.getExtent().containsLonLat(centerLonLat);
            if(visible !== this.isVisible()) {
                this.setVisible(visible);
            }
        }

        if(this.isVisible()) {
            var centerPx = this.map.getViewPortPxFromLonLat(centerLonLat);
            var mapBox = Ext.fly(this.map.div).getBox(); 
    
            //This works for positioning with the anchor on the bottom.
            
            //Will have to functionalize this out later and allow
            //for other positions relative to the feature.
            var anchorSelector = "div." + this.ancCls;
    
            var dx = this.anc.down(anchorSelector).getLeft(true) +
                                this.anc.down(anchorSelector).getWidth() / 2;
            var dy = this.el.getHeight();
    
            //Assuming for now that the map viewport takes up
            //the entire area of the MapPanel
            this.setPosition(centerPx.x + mapBox.x - dx, centerPx.y + mapBox.y - dy);
        }
    },

    /** private: method[getAnchorElement]
     *  :returns: ``Ext.Element``  The anchor element of the popup.
     */
    getAnchorElement: function() {
        var anchorSelector = "div." + this.ancCls;
        var anc = Ext.get(this.el.child(anchorSelector));
        return anc;
    },

    /** private: method[unanchorPopup]
     *  Unanchors a popup from its feature.  This removes the popup from its
     *  MapPanel and adds it to the page body.
     */
    unanchorPopup: function() {
        this.removeAnchorEvents();
        //make the window draggable
        this.draggable = true;
        this.header.addClass("x-window-draggable");
        this.dd = new Ext.Window.DD(this);

        if(this.maximizable)
            this.tools.maximize.show();
        
        /* this.addTool({
                id: 'maximize',
                handler: function(event, toolEl, panel){
                    panel.maximize();
                    panel.tools.maximize.hide();
                    panel.addTool({
                        id: 'restore',
                        handler: function(event, toolEl, panel){
                            panel.restore();
                            panel.tools.restore.hide();
                            panel.tools.maximize.show();
                        }
                    });
                }
            });*/
        //remove anchor
        this.getAnchorElement().remove();
        this.anc = null;

        //hide unpin tool
        this.tools.unpin.hide();


    },

    /** private: method[panIntoView]
     *  Pans the MapPanel's map so that an anchored popup can come entirely
     *  into view, with padding specified as per normal OpenLayers.Map popup
     *  padding.
     */ 
    panIntoView: function() {
        var centerLonLat = this.feature.geometry.getBounds().getCenterLonLat();
        var centerPx = this.map.getViewPortPxFromLonLat(centerLonLat);
        var mapBox = Ext.fly(this.map.div).getBox(); 

        //assumed viewport takes up whole body element of map panel
        var popupPos =  this.getPosition(true);
        popupPos[0] -= mapBox.x;
        popupPos[1] -= mapBox.y;
       
        var panelSize = [mapBox.width, mapBox.height]; // [X,Y]

        var popupSize = this.getSize();

        var newPos = [popupPos[0], popupPos[1]];

        //For now, using native OpenLayers popup padding.  This may not be ideal.
        var padding = this.map.paddingForPopups;

        // X
        if(popupPos[0] < padding.left) {
            newPos[0] = padding.left;
        } else if(popupPos[0] + popupSize.width > panelSize[0] - padding.right) {
            newPos[0] = panelSize[0] - padding.right - popupSize.width;
        }

        // Y
        if(popupPos[1] < padding.top) {
            newPos[1] = padding.top;
        } else if(popupPos[1] + popupSize.height > panelSize[1] - padding.bottom) {
            newPos[1] = panelSize[1] - padding.bottom - popupSize.height;
        }

        var dx = popupPos[0] - newPos[0];
        var dy = popupPos[1] - newPos[1];

        this.map.pan(dx, dy);
    },
    
    /** private: method[onMapMove]
     */
    onMapMove: function() {
        this._mapMove = true;
        this.position();
        delete this._mapMove;
    },
    
    /** private: method[addAnchorEvents]
     */
    addAnchorEvents: function() {
        this.map.events.on({
            "move" : this.onMapMove,
            scope : this            
        });
        
        this.on({
            "resize": this.position,
            "collapse": this.position,
            "expand": this.position,
            scope: this
        });
    },
    
    /** private: method[removeAnchorEvents]
     */
    removeAnchorEvents: function() {
        //stop position with feature
        this.map.events.un({
            "move" : this.onMapMove,
            scope : this
        });

        this.un("resize", this.position, this);
        this.un("collapse", this.position, this);
        this.un("expand", this.position, this);

    },

    /** private: method[beforeDestroy]
     *  Cleanup events before destroying the popup.
     */
    beforeDestroy: function() {
        if(this.anchored) {
            this.removeAnchorEvents();
        }
        GeoExt.Popup.superclass.beforeDestroy.call(this);
    }
});

/** api: xtype = gx_popup */
Ext.reg('gx_popup', GeoExt.Popup); 
