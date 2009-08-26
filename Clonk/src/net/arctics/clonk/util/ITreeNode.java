package net.arctics.clonk.util;

import java.util.Collection;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.TreePath;

public interface ITreeNode extends INode {
	ITreeNode getParentNode();
	IPath getPath();
	Collection<? extends INode> getChildCollection();
	boolean subNodeOf(ITreeNode node);
	void addChild(ITreeNode node);	

	public static class Helpers {
		public static TreePath getTreePath(ITreeNode node) {
			int num;
			ITreeNode n;
			for (num = 0, n = node; n != null; n = n.getParentNode(), num++);
			Object[] path = new Object[num];
			for (num = 0, n = node; n != null; n = n.getParentNode(), num++)
				path[path.length-num-1] = n;
			return new TreePath(path);
		}
	}
	
	public static class Default {
		public static IPath getPath(ITreeNode node) {
			return node.getParentNode() != null ? node.getParentNode().getPath().append(node.getNodeName()) : new Path(node.getNodeName());
		}
		public static boolean subNodeOf(ITreeNode node, ITreeNode other) {
			for (ITreeNode n = node; n != null; n = n.getParentNode())
				if (n == other)
					return true;
			return false;
		}
	}
	
}
