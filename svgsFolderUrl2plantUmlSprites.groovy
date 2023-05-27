#!/usr/bin/env groovy
@Grab('net.sourceforge.plantuml:plantuml:1.2023.8')
@Grab('org.apache.xmlgraphics:batik-transcoder:1.16')
@Grab('org.apache.xmlgraphics:batik-codec:1.16')
@Grab('org.codehaus.gpars:gpars:1.2.1')

import groovy.cli.commons.CliBuilder
import groovy.json.JsonSlurper
import groovyx.gpars.GParsPool
import net.sourceforge.plantuml.klimt.sprite.SpriteGrayLevel
import net.sourceforge.plantuml.klimt.sprite.SpriteUtils
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder

import javax.imageio.ImageIO
import java.awt.*
import java.awt.image.BufferedImage
import java.nio.file.Paths

final DEFAULT_SCALE = 0.2
final TMP_DIR = new File('/tmp/svgsFolderUrl2plantUmlSprites')
TMP_DIR.mkdirs()
final SPRITES_DIR = new File('sprites')
SPRITES_DIR.mkdirs()
final PNGS_DIR = new File('pngs')
PNGS_DIR.mkdirs()
final SPRITES_LISTING = new SpritesListing(new File('sprites-list.md'), PNGS_DIR)

def cli = new CliBuilder(usage: "${this.class.getSimpleName()}.groovy [options] <svgs URL>", stopAtNonOption: false, footer: "Usage example: ./${this.class.getSimpleName()}.groovy https://github.com/gilbarbara/logos/tree/master/logos")
cli.s(longOpt: 'scale', args: 1, argName: 'scale', "Scale (eg: 0.5 to reduce size to half) of generated sprites. Default value: $DEFAULT_SCALE")
cli.c(longOpt: 'use-cache', "When specified, already downloaded files are not re-downloaded")
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
def useCache = options.c

GParsPool.withPool {
    listSvgsUrls(svgsUrl)
            .collectParallel {
                downloadFile(it, TMP_DIR, useCache)
            }
            .collectParallel {
                svg2Png(it, PNGS_DIR)
            }
            .collectParallel {
                scaleImage(it, scaleFactor)
            }
            .collectParallel {
                png2PlantUmlSprite(it, SPRITES_DIR)
            }
            .collectParallel {
                it.getName().replace('.puml', '')
            }
            .toSorted()
            .each {
                SPRITES_LISTING.addSprite(it)
            }
}
SPRITES_LISTING.addPendingSprites('')

static def listSvgsUrls(baseUrl) {
    def matcher = baseUrl =~ /^https:\/\/github.com\/([^\/]+)\/([^\/]+)\/tree\/(.*)$/
    if (!matcher.matches()) {
        throw new IllegalArgumentException("Provided URL is not a GitHub folder URL")
    }
    def owner = matcher[0][1]
    def repo = matcher[0][2]
    def path = matcher[0][3]

    def treeUrl = findTreeUrl("https://api.github.com/repos/${owner}/${repo}/git/trees", path.split('/'))
    return getUrlJson(treeUrl).tree.collect {
        "https://raw.githubusercontent.com/${owner}/${repo}/${path}/${it.path}"
    }
}

static def findTreeUrl(treesBaseUrl, pathParts) {
    def ret = "${treesBaseUrl}/${pathParts[0]}"
    for (def i = 1; i < pathParts.size(); i++) {
        def pathNode = getUrlJson(ret).tree.find { it.path == pathParts[i] }
        if (!pathNode) {
            throw new IllegalArgumentException("Not found provided path in GitHub")
        }
        ret = pathNode.url
    }
    return ret
}

static def getUrlJson(url) {
    return new JsonSlurper()
            .parseText(new URL(url).text)
}

static def downloadFile(url, workDir, useCache) {
    println("Downloading ${url} ...")
    def fileName = Paths.get(new URI(url).path).fileName
    def svgFile = new File("$workDir/${fileName}")
    if (useCache && svgFile.exists()) {
        return svgFile
    }
    svgFile.delete()
    svgFile << new URL(url).text
    return svgFile
}

static def svg2Png(svg, workDir) {
    println("Converting ${svg} to png ...")
    def fileName = svg.name.replace(".svg", ".png")
    def pngFile = new File("$workDir/$fileName")
    pngFile.delete()
    OutputStream pngOut = new FileOutputStream(pngFile)
    new PNGTranscoder().transcode(new TranscoderInput(svg.toURI().toString()), new TranscoderOutput(pngOut))
    pngOut.flush()
    pngOut.close()
    return pngFile
}

static def scaleImage(png, scaleFactor) {
    BufferedImage im = ImageIO.read(png)
    def width = (im.width * scaleFactor) as Integer
    def height = (im.height * scaleFactor) as Integer
    BufferedImage scaledImage = new BufferedImage(width, height, im.type)
    Graphics2D graphics2D = scaledImage.createGraphics()
    graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics2D.drawImage(im, 0, 0, width, height, null)
    graphics2D.dispose()
    ImageIO.write(scaledImage, 'png', png);
    return png
}

static def png2PlantUmlSprite(png, outputDir) {
    println("Converting ${png} to puml ...")
    BufferedImage im = ImageIO.read(png)
    removeAlpha(im)
    String spriteName = png.name.replace(".png", "")
    def spriteFile = new File("$outputDir/${spriteName}.puml")
    spriteFile.delete()
    spriteFile << "@startuml\n" + SpriteUtils.encode(im, spriteName, SpriteGrayLevel.GRAY_16) + "@enduml\n"
}

static def removeAlpha(im) {
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

class SpritesListing {

    File listFile
    String pngsPath
    Iterator<String> existingSprites

    SpritesListing(listFile, pngsPath) {
        this.pngsPath = pngsPath
        this.listFile = listFile
        this.existingSprites = pngsPath.listFiles()
                .collect { it.name.replace('.png', '') }
                .toSorted()
                .iterator()
        listFile.delete()
        listFile << '''# Sprites list

* 🧟 marked sprites are no longer in [Gil Barbara's logos repository](https://github.com/gilbarbara/logos) and may be removed in the future. Consider stop using them and look for alternatives.

| Sprite | Icon |
|--------|------|
'''
    }

    def addSprite(spriteName) {
        addPendingSprites(spriteName)
        addSpriteToFile(spriteName, '')
    }

    def addPendingSprites(nextSprite) {
        while (existingSprites.hasNext()) {
            String sprite = existingSprites.next()
            if (nextSprite == sprite) {
                return
            }
            addSpriteToFile(sprite, '🧟 ')
        }
    }

    def addSpriteToFile(spriteName, statusPrefix) {
        listFile << "|$statusPrefix$spriteName|![$spriteName]($pngsPath/${spriteName}.png)|\n"
    }

}
