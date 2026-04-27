import AppKit
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

struct DemoFrame {
    let imagePath: String
    let eyebrow: String
    let title: String
    let body: String
}

let root = FileManager.default.currentDirectoryPath
let outputDirectory = "\(root)/website/public/demo"
let outputPath = "\(outputDirectory)/falkenr-demo.gif"

let frames = [
    DemoFrame(
        imagePath: "\(root)/website/public/screenshots/website-home.png",
        eyebrow: "1. Install",
        title: "Add one Spring Boot dependency",
        body: "Restart locally and open /_dev."
    ),
    DemoFrame(
        imagePath: "\(root)/website/public/screenshots/dashboard-live.png",
        eyebrow: "2. Inspect",
        title: "See the live runtime dashboard",
        body: "Requests, logs, config, jobs, DB queries, flags, and more."
    ),
    DemoFrame(
        imagePath: "\(root)/website/public/screenshots/dashboard-live.png",
        eyebrow: "3. Share",
        title: "Send the debugging session",
        body: "Invite a teammate when screenshots and repro notes are not enough."
    ),
    DemoFrame(
        imagePath: "\(root)/website/public/screenshots/dashboard-live.png",
        eyebrow: "4. Collaborate",
        title: "Debug from the same runtime state",
        body: "Hosted collaboration builds on the same local session."
    ),
]

try FileManager.default.createDirectory(atPath: outputDirectory, withIntermediateDirectories: true)

func color(_ hex: UInt32) -> NSColor {
    let r = CGFloat((hex >> 16) & 0xff) / 255.0
    let g = CGFloat((hex >> 8) & 0xff) / 255.0
    let b = CGFloat(hex & 0xff) / 255.0
    return NSColor(red: r, green: g, blue: b, alpha: 1.0)
}

func drawText(_ text: String, in rect: CGRect, font: NSFont, color: NSColor, paragraph: NSMutableParagraphStyle? = nil) {
    var attributes: [NSAttributedString.Key: Any] = [
        .font: font,
        .foregroundColor: color,
    ]
    if let paragraph {
        attributes[.paragraphStyle] = paragraph
    }
    text.draw(in: rect, withAttributes: attributes)
}

func renderFrame(_ frame: DemoFrame) throws -> CGImage {
    let size = CGSize(width: 960, height: 640)
    let bitmap = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: Int(size.width),
        pixelsHigh: Int(size.height),
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    )!

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)

    color(0xf6f0e6).setFill()
    CGRect(origin: .zero, size: size).fill()

    let screenshot = NSImage(contentsOfFile: frame.imagePath)!
    let imageRect = CGRect(x: 36, y: 156, width: 888, height: 426)
    color(0xffffff).setFill()
    imageRect.insetBy(dx: -12, dy: -12).fill()
    color(0xd7ccb7).setStroke()
    NSBezierPath(rect: imageRect.insetBy(dx: -12, dy: -12)).stroke()
    screenshot.draw(in: imageRect, from: .zero, operation: .sourceOver, fraction: 1.0)

    let titleParagraph = NSMutableParagraphStyle()
    titleParagraph.lineBreakMode = .byWordWrapping

    drawText(
        frame.eyebrow.uppercased(),
        in: CGRect(x: 44, y: 98, width: 860, height: 22),
        font: NSFont.systemFont(ofSize: 13, weight: .semibold),
        color: color(0xb4512f)
    )
    drawText(
        frame.title,
        in: CGRect(x: 44, y: 52, width: 860, height: 42),
        font: NSFont.systemFont(ofSize: 32, weight: .semibold),
        color: color(0x10131c),
        paragraph: titleParagraph
    )
    drawText(
        frame.body,
        in: CGRect(x: 44, y: 24, width: 860, height: 24),
        font: NSFont.systemFont(ofSize: 16, weight: .regular),
        color: color(0x5b6171)
    )

    NSGraphicsContext.restoreGraphicsState()
    return bitmap.cgImage!
}

let outputUrl = URL(fileURLWithPath: outputPath)
guard let destination = CGImageDestinationCreateWithURL(
    outputUrl as CFURL,
    UTType.gif.identifier as CFString,
    frames.count,
    nil
) else {
    fatalError("Could not create GIF destination")
}

let gifProperties: [CFString: Any] = [
    kCGImagePropertyGIFDictionary: [
        kCGImagePropertyGIFLoopCount: 0,
    ],
]
CGImageDestinationSetProperties(destination, gifProperties as CFDictionary)

for frame in frames {
    let image = try renderFrame(frame)
    let frameProperties: [CFString: Any] = [
        kCGImagePropertyGIFDictionary: [
            kCGImagePropertyGIFDelayTime: 1.45,
        ],
    ]
    CGImageDestinationAddImage(destination, image, frameProperties as CFDictionary)
}

if !CGImageDestinationFinalize(destination) {
    fatalError("Could not finalize GIF")
}

print(outputPath)
