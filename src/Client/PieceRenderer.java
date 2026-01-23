package Client;

import Game.Piece;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.effect.DropShadow;

/**
 * Renders Quarto pieces as JavaFX nodes.
 */
public class PieceRenderer {

    // Colors
    private static final Color DARK_COLOR = Color.MIDNIGHTBLUE;
    private static final Color LIGHT_COLOR = Color.CRIMSON;
    private static final Color HOLLOW_FILL = Color.TRANSPARENT;

    /**
     * Creates a JavaFX node representing the piece.
     * @param piece the piece to render
     * @param size base size (width/diameter) in pixels
     * @return the Node
     */
    public static Node render(Piece piece, double size) {
        if (piece == null) {
            return new StackPane(); // Empty placeholder
        }

        Shape shape;
        double strokeWidth = size * 0.1;
        
        // 1. Shape: Round vs Square
        if (piece.isRound) {
            shape = new Circle(size / 2.0);
        } else {
            shape = new Rectangle(size, size);
        }

        // 2. Color: Dark vs Light (Determines Stroke/Fill color base)
        Color baseColor = piece.isDark ? DARK_COLOR : LIGHT_COLOR;

        // 3. Solidity: Hollow vs Solid
        if (piece.isHollow) {
            shape.setFill(HOLLOW_FILL);
            shape.setStroke(baseColor);
            shape.setStrokeWidth(strokeWidth);
        } else {
            shape.setFill(baseColor);
            shape.setStroke(baseColor); // Optional
            shape.setStrokeWidth(0);
        }

        // 4. Height: Tall vs Short
        // We render 'Tall' as full size, 'Short' as slightly smaller
        double scale = piece.isTall ? 1.0 : 0.7;
        shape.setScaleX(scale);
        shape.setScaleY(scale);
        
        // Add a subtle shadow for depth
        DropShadow shadow = new DropShadow();
        shadow.setRadius(5.0);
        shadow.setOffsetX(3.0);
        shadow.setOffsetY(3.0);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        shape.setEffect(shadow);

        StackPane container = new StackPane(shape);
        // Ensure container takes up full 'size' space even if shape is scaled down
        container.setPrefSize(size, size);
        container.setMinSize(size, size);
        container.setMaxSize(size, size);
        
        return container;
    }
}
