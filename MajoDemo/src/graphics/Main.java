package graphics;

import java.awt.Dimension;

import javax.swing.JFrame;

import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;

public class Main {

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		final Graphics graphic = new Graphics();
		// Layout<V, E>, VisualizationComponent<V,E>
		final Layout<String, String> layout = 
				new FRLayout<>(graphic.getForest());
		layout.setSize(new Dimension(600,600));
		final VisualizationViewer<String,String> vv = 
				new VisualizationViewer<String,String>(layout);
		vv.setPreferredSize(new Dimension(800,800));
		
		// Show vertex and edge labels
		vv.getRenderContext().setVertexLabelTransformer(new ToStringLabeller());
		vv.getRenderContext().setEdgeLabelTransformer(new ToStringLabeller());
		
		
		
		// Create a graph mouse and add it to the visualization component
		final DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
		gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
		vv.setGraphMouse(gm); 
		final JFrame frame = new JFrame("Nutzung des B+R Systems");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(vv);
		frame.pack();
		frame.setVisible(true);   

	}

}
