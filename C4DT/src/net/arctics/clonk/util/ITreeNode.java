package net.arctics.clonk.util;

import java.util.Collection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

public interface ITreeNode extends INodeWithPath {
	@Override
	ITreeNode parentNode();
	Collection<? extends INode> childCollection();
	boolean subNodeOf(ITreeNode node);
	void addChild(ITreeNode node);	
	public static class Default {
		public static IPath path(final INodeWithPath node) {
			return node.parentNode() != null ? node.parentNode().path().append(node.nodeName()) : new Path(node.nodeName());
		}
		public static boolean subNodeOf(final ITreeNode node, final ITreeNode other) {
			for (ITreeNode n = node; n != null; n = n.parentNode())
				if (n == other)
					return true;
			return false;
		}
		public static IPath pathRelativeTo(final ITreeNode item, final ITreeNode other) {
			if (item == other)
				return Path.EMPTY;
			else if (item.parentNode() == null)
				return new Path(item.nodeName());
			else
				return pathRelativeTo(item.parentNode(), other).append(item.nodeName());
		}
		public static IPath relativePath(final INodeWithPath node, final INodeWithPath superNode) {
			return node.parentNode() != null && node.parentNode() != superNode ? relativePath(node.parentNode(), superNode).append(node.nodeName()) : new Path(node.nodeName());
		}
	}
}
