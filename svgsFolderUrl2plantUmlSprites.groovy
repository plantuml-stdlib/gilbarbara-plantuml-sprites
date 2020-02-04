#!/usr/bin/env groovy

@Grab('net.sourceforge.plantuml:plantuml:1.2020.0')
@Grab('org.apache.xmlgraphics:batik-transcoder:1.9.1')
@Grab('org.apache.xmlgraphics:batik-codec:1.9.1')

import groovy.json.JsonSlurper
import java.net.URI
import java.nio.file.Paths


import groovy.util.XmlSlurper
import groovy.xml.StreamingMarkupBuilder
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import net.sourceforge.plantuml.sprite.SpriteGrayLevel
import net.sourceforge.plantuml.sprite.SpriteUtils
import org.apache.batik.transcoder.image.PNGTranscoder
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput

final DEFAULT_SCALE = 0.2
final TMP_DIR = new File('/tmp/svgsFolderUrl2plantUmlSprites')
TMP_DIR.mkdirs()
final SPRITES_DIR = new File('sprites')
SPRITES_DIR.mkdirs()
final PNGS_DIR = new File('pngs')
PNGS_DIR.mkdirs()
final SPRITES_LISTING = new SpritesListing(new File('sprites-list.md'), PNGS_DIR)

def cli = new CliBuilder(usage: "${this.class.getSimpleName()}.groovy [options] <svgs URL>", stopAtNonOption: false, footer: "Usage example: ./${this.class.getSimpleName()}.groovy https://api.github.com/repos/gilbarbara/logos/contents/logos")
cli.s(longOpt: 'scale', args: 1, argName: 'scale', "Scale (eg: 0.5 to reduce size to half) of generated sprites. Default value: $DEFAULT_SCALE")
def options = cli.parse(args)
!options && System.exit(1)
if (!options.arguments()) {
  println "error: Missing required svgs URL"
  cli.usage()
  System.exit(1)
}
if (options.arguments().size() > 1) {
  println "error: Only one svgs URL is supported"
  cli.usage()
  System.exit(1)
}
def svgsUrl = options.arguments()[0]
def scaleFactor = options.s ?: DEFAULT_SCALE

listSvgsUrls(svgsUrl)
  .collect {
    downloadFile(it, TMP_DIR)
  }
  .collect {
    svg2Png(it, PNGS_DIR)
  }
  .collect {
    png2PlantUmlSprite(it, scaleFactor, SPRITES_DIR)
  }
  .each {
    SPRITES_LISTING.addSprite(it)
  }

def listSvgsUrls(baseUrl) {
  new JsonSlurper()
    .parseText(new URL(baseUrl).text)
    .collect{ it['download_url'] }
}

def downloadFile(url, workDir) {
  def fileName = Paths.get(new URI(url).path).fileName
  def svgFile = new File("$workDir/${fileName}")
  svgFile.delete()
  svgFile << new URL(url).text
  return svgFile
}

def svg2Png(svg, workDir) {
  def fileName = svg.name.replace(".svg",".png")
  pngFile = new File("$workDir/$fileName")
  pngFile.delete()
  OutputStream pngOut = new FileOutputStream(pngFile)
  new PNGTranscoder().transcode(new TranscoderInput(svg.toURI().toString()), new TranscoderOutput(pngOut))
  pngOut.flush()
  pngOut.close()
  return pngFile
}

def png2PlantUmlSprite(png, scaleFactor, outputDir) {
  BufferedImage im = ImageIO.read(png)
  removeAlpha(im)
  im = scaleImage(im, scaleFactor)
  String spriteName = png.name.replace(".png","")
  def spriteFile = new File("$outputDir/${spriteName}.puml")
  spriteFile.delete()
  spriteFile << "@startuml\n" + SpriteUtils.encode(im, spriteName, SpriteGrayLevel.GRAY_16) + "@enduml\n"
}

def removeAlpha(im) {
  Graphics2D graphics = im.createGraphics()
  try {
    graphics.setComposite(AlphaComposite.DstOver)
    graphics.setPaint(Color.WHITE)
    graphics.fillRect(0, 0, im.getWidth(), im.getHeight())
  }
  finally {
    graphics.dispose()
  }
}

def scaleImage(im, scaleFactor) {
  def width = (im.width * scaleFactor) as Integer
  def height = (im.height * scaleFactor) as Integer
  BufferedImage ret = new BufferedImage(width, height, im.getType());
  Graphics2D graphics2D = ret.createGraphics();
  graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
  graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
  graphics2D.drawImage(im, 0, 0, width, height, null);
  graphics2D.dispose();
  return ret;
}

class SpritesListing {

  File listFile
  String pngsPath

  def SpritesListing(listFile, pngsPath) {
    this.pngsPath = pngsPath
    this.listFile = listFile
    listFile.delete()
    listFile << '''# Sprites list
| Sprite | Icon |
|--------|------|
'''
  }

  def addSprite(spriteFile) {
    def spriteName = spriteFile.name.replace('.puml', '')
    listFile << "|$spriteName|![$spriteName]($pngsPath/${spriteName}.png)|\n"
  }

}
