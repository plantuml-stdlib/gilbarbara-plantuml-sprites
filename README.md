# gilbarbara-plantuml-sprites

This repository contains PlantUML sprites generated from [Gil Barbara's logos](https://github.com/gilbarbara/logos), which can easily be used in PlantUML diagrams for nice visual aid.

This project is inspired in other PlantUML sprites repositories like [AWS-PlantUML](https://github.com/milo-minderbinder/AWS-PlantUML) and [PlantUML Icon-Font Sprites](https://github.com/tupadr3/plantuml-icon-font-sprites). Check [PlantUML stdlib](https://plantuml.com/es/stdlib) for a list of other similar repositories included by default by PlantUML.

## Usage

Just import the proper sprite into your PlantUML diagram and use it like any other sprite.

Example:

```
@startuml

!define SPRITESURL https://raw.githubusercontent.com/plantuml-stdlib/gilbarbara-plantuml-sprites/v1.0/sprites
!includeurl SPRITESURL/flask.puml
!includeurl SPRITESURL/kafka.puml
!includeurl SPRITESURL/kotlin.puml
!includeurl SPRITESURL/cassandra.puml

title Gil Barbara's logos example

skinparam monochrome true

rectangle "<$flask>\nwebapp" as webapp
queue "<$kafka>" as kafka
rectangle "<$kotlin>\ndaemon" as daemon
database "<$cassandra>" as cassandra

webapp -> kafka
kafka -> daemon
daemon --> cassandra 

@enduml
```

![Example](http://www.plantuml.com/plantuml/png/VO_FIWCn4CRlUOgq29vsLwyYfGWYWWTfzUPbTZFTXisVc9bO7zyqMHOzM0Z9JBvlVZEp9OR8oHeXLWeFsg7Sl-wUtnxtxxiNUMGETDTr4KxjgFcO-aGO1-yO7RU3jrdfqQ1Jq8tQz13pWIOOS6TcGo5gHkg-RjkRRax4Ihl198Kfcb-zkeC1cjgGo_vpJ72OuBB3iz7kecK08d0gpY31cWtA9staGvt-cgJneoU9ts23GI5eqYvanKhhdH-EqLkK75EM8WuCRio4zVrLsM3puKxOGmXh2IHhA3uJfc8fAsoALCA_W3f-9YZ0tAhOQYWKCFH0nRQiK45BAaHj9vlDJ2_tEPqfzP5D_bcgf11RT2fPymC0)

The list of available sprites is [here](sprites-list.md).

You can play around and test ideas with [Online PlantUML Editor](http://plantuml.com/plantuml/uml).

## Build

Sprites are built with provided [svgsFolderUrl2plantUmlSprites.groovy](svgsFolderUrl2plantUmlSprites.groovy) script. To update sprites from icons in [Gil Barbara's repo](https://github.com/gilbarbara/logos) just re-run:

```bash
./svgsFolderUrl2plantUmlSprites.groovy https://github.com/gilbarbara/logos/tree/master/logos
```

## Note

* All logo icons are the registered trademarks of their respective owners.
