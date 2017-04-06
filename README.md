#Bookmarx 

![alt text](https://www.browncross.com/bookmarx/images/black-star.png "Bookmarx")

A personal bookmark tracer written in [Clojure](https//clojure.org) and [ClojureScript](https://github.com/clojure/clojurescript). It is essentially a rewrite, sans gold-plating, of the excellent [Bookmark4U](http://bookmark4u.sourceforge.net) which I used for many years, and whose interface and source code have unfortunately become pretty dated. Kudos to the authors of this application.

On the front-end it is using [Reagent](https://reagent-project.github.io/), which is a ClojureScript wrapper for the [React](https://facebook.github.io/react/) user interface framework. The user interface style is the ubiquitous [Bootstrap](http://getbootstrap.com) framework. On the back-end it is using Redis for persistence.

This is written for my own edification and provided as-as and is not supported.

The icons in this application are from [Gylphicons](http://glyphicons.com) which are part of the Bootstrap framework.

This link can be put on the favorites bar to add links to Bookmarx:
javascript:void(open('https://example.com/bookmarx/add?url='+escape(document.location)+'&title='+escape(document.title),'bookmarx','width=770,height=300,scrollbars=1,resizable=1'));
