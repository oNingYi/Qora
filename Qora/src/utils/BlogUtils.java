package utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.StringUtil;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import qora.account.PublicKeyAccount;
import qora.crypto.Base58;
import qora.naming.Name;
import qora.transaction.ArbitraryTransaction;
import qora.transaction.Transaction;
import qora.web.BlogBlackWhiteList;
import qora.web.BlogProfile;
import qora.web.NameStorageMap;
import qora.web.Profile;
import qora.web.blog.BlogEntry;
import api.BlogPostResource;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.twitter.Extractor;

import controller.Controller;
import database.DBSet;
import database.PostCommentMap;

public class BlogUtils {
	/**
	 * 
	 * @return triplet of name, title, description of all enabled blogs.
	 */
	public static List<BlogProfile> getEnabledBlogs(String searchvalueOpt) {
		NameStorageMap nameMap = DBSet.getInstance().getNameStorageMap();
		Set<String> names = nameMap.getKeys();

		Map<String, List<String>> followMap = new HashMap<>();
		List<Profile> resultProfiles = new ArrayList<>();

		for (String name : names) {
			Profile profile = Profile.getProfileOpt(name);

			if (profile == null || !profile.isProfileEnabled())
				continue;

			List<String> followedBlogs = profile.getFollowedBlogs();
			if (followedBlogs != null) {
				List<String> alreadyProcessed = new ArrayList<String>();

				for (String followedBlog : followedBlogs) {
					if (alreadyProcessed.contains(followedBlog) || name.equals(followedBlog))
						continue;

					alreadyProcessed.add(followedBlog);

					if (followMap.containsKey(followedBlog)) {
						List<String> followerList = followMap.get(followedBlog);

						if (!followerList.contains(name))
							followerList.add(name);

						followMap.put(followedBlog, followerList);
					} else {
						List<String> followerList = new ArrayList<>();
						followerList.add(name);
						followMap.put(followedBlog, followerList);
					}
				}
			}

			if (profile.isBlogEnabled()) {
				String title = profile.getBlogTitleOpt();
				String description = profile.getBlogDescriptionOpt();

				if (searchvalueOpt != null) {
					searchvalueOpt = searchvalueOpt.toLowerCase();

					if (name.toLowerCase().contains(searchvalueOpt) || (title != null && title.toLowerCase().contains(searchvalueOpt))
							|| (description != null) && description.toLowerCase().contains(searchvalueOpt)) {
						resultProfiles.add(profile);
					}

					continue;
				}

				resultProfiles.add(profile);
			}
		}

		List<BlogProfile> blogprofiles = new ArrayList<>();
		for (Profile profileWithBlog : resultProfiles) {
			String name = profileWithBlog.getName().getName();

			if (followMap.containsKey(name)) {
				blogprofiles.add(new BlogProfile(profileWithBlog, followMap.get(name)));
			} else {
				blogprofiles.add(new BlogProfile(profileWithBlog, new ArrayList<String>()));
			}
		}

		Collections.sort(blogprofiles);

		return blogprofiles;
	}

	public static List<BlogEntry> getBlogPosts(List<String> blogList) {
		List<BlogEntry> blogPosts = new ArrayList<BlogEntry>();

		for (String blogname : blogList)
			blogPosts.addAll(getBlogPosts(blogname));

		Collections.sort(blogPosts, new BlogEntryTimestampComparator());

		Collections.reverse(blogPosts);

		return blogPosts;
	}

	public static List<BlogEntry> getHashTagPosts(String hashtag) {
		List<BlogEntry> results = new ArrayList<BlogEntry>();
		List<byte[]> list = DBSet.getInstance().getHashtagPostMap().get(hashtag);

		if (list != null) {
			for (byte[] bs : list) {
				BlogEntry blogEntryOpt = getBlogEntryOpt(bs);

				if (blogEntryOpt != null)
					results.add(blogEntryOpt);
			}
		}

		Collections.sort(results, new BlogEntryTimestampComparator());

		Collections.reverse(results);

		return results;
	}

	public static List<String> getHashTags(String text) {
		List<String> extractHashtags = new Extractor().extractHashtags(text);
		List<String> result = new ArrayList<String>();

		for (String hashTag : extractHashtags)
			result.add("#" + hashTag);

		return result;
	}

	public static List<String> getBlogTags(String text) {
		List<String> extractScreenNames = new Extractor().extractMentionedScreennames(text);
		List<String> result = new ArrayList<String>();

		for (String screenNames : extractScreenNames)
			result.add("@" + screenNames);

		return result;
	}

	public static List<BlogEntry> getBlogPosts(String blogOpt) {
		return getBlogPosts(blogOpt, -1);
	}

	public static List<BlogEntry> getCommentBlogPosts(String signatureOfBlogPost) {
		return getCommentBlogPosts(signatureOfBlogPost, -1);
	}

	public static List<BlogEntry> getCommentBlogPosts(String signatureOfBlogPost, int limit) {
		List<BlogEntry> results = new ArrayList<>();

		PostCommentMap commentPostMap = DBSet.getInstance().getPostCommentMap();

		List<byte[]> list = commentPostMap.get(Base58.decode(signatureOfBlogPost));

		Collections.reverse(list);

		List<ArbitraryTransaction> blogPostTX = new ArrayList<>();

		if (list != null) {
			for (byte[] blogArbTx : list) {
				Transaction transaction = Controller.getInstance().getTransaction(blogArbTx);

				if (transaction != null)
					blogPostTX.add((ArbitraryTransaction) transaction);
			}
		}

		int i = 0;

		for (ArbitraryTransaction transaction : blogPostTX) {
			// String creator = transaction.getCreator().getAddress();

			// TODO ARE COMMENTS ALLOWED CHECK!
			// BlogBlackWhiteList blogBlackWhiteList = BlogBlackWhiteList
			// .getBlogBlackWhiteList(blogOpt);

			BlogEntry blogEntry = getCommentBlogEntryOpt(transaction);

			// String nameOpt = blogEntry.getNameOpt();
			if (blogEntry != null) {
				results.add(blogEntry);
				i++;
			}
			// if (blogBlackWhiteList.isAllowedPost(
			// nameOpt != null ? nameOpt : creator, creator)) {
			// results.add(blogEntry);
			// i ++;
			// }

			if (i == limit)
				break;
		}

		return results;

	}

	public static List<BlogEntry> getBlogPosts(String blogOpt, int limit) {
		List<BlogEntry> results = new ArrayList<>();

		List<byte[]> blogPostList = DBSet.getInstance().getBlogPostMap().get(blogOpt == null ? "QORA" : blogOpt);

		List<byte[]> list = blogPostList != null ? Lists.newArrayList(blogPostList) : new ArrayList<byte[]>();

		Collections.reverse(list);

		List<ArbitraryTransaction> blogPostTX = new ArrayList<>();

		if (list != null) {
			for (byte[] blogArbTx : list) {
				Transaction transaction = Controller.getInstance().getTransaction(blogArbTx);

				if (transaction != null)
					blogPostTX.add((ArbitraryTransaction) transaction);
			}
		}

		int i = 0;

		for (ArbitraryTransaction transaction : blogPostTX) {
			String creator = transaction.getCreator().getAddress();

			BlogBlackWhiteList blogBlackWhiteList = BlogBlackWhiteList.getBlogBlackWhiteList(blogOpt);

			BlogEntry blogEntry = getBlogEntryOpt(transaction);

			String nameOpt;

			if (blogEntry != null) {
				if (blogEntry.getShareAuthorOpt() != null)
					nameOpt = blogEntry.getShareAuthorOpt();
				else
					nameOpt = blogEntry.getNameOpt();

				if (blogBlackWhiteList.isAllowedPost(nameOpt != null ? nameOpt : creator, creator)) {
					results.add(blogEntry);
					i++;
				}
			}

			if (i == limit)
				break;
		}

		return results;

	}

	public static void processBlogPost(byte[] data, byte[] signature, PublicKeyAccount creator, DBSet db) {
		String string = new String(data, Charsets.UTF_8);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(string);

		if (jsonObject == null)
			return;

		String post = (String) jsonObject.get(BlogPostResource.POST_KEY);
		String blognameOpt = (String) jsonObject.get(BlogPostResource.BLOGNAME_KEY);
		String share = (String) jsonObject.get(BlogPostResource.SHARE_KEY);
		String delete = (String) jsonObject.get(BlogPostResource.DELETE_KEY);
		String author = (String) jsonObject.get(BlogPostResource.AUTHOR);

		boolean isShare = false;

		if (StringUtils.isNotEmpty(share)) {
			isShare = true;
			byte[] sharedSignature = Base58.decode(share);

			if (sharedSignature != null)
				db.getSharedPostsMap().add(sharedSignature, author);
		}

		if (StringUtils.isNotEmpty(delete)) {
			BlogEntry blogEntryOpt = BlogUtils.getBlogEntryOpt(delete);

			if (blogEntryOpt == null)
				return;

			String creatorOfDeleteTX = creator.getAddress();
			String creatorOfEntryToDelete = blogEntryOpt.getCreator();

			if (creatorOfDeleteTX.equals(creatorOfEntryToDelete)) {
				// Post's owner is deleting their own post
				deleteBlogPost(db, isShare, blogEntryOpt);
			} else if (author != null && blogEntryOpt.getBlognameOpt() != null) {
				// Blog's [name's] owner is deleting the post
				// XXX: "author" is unused?
				Name name = db.getNameMap().get(blogEntryOpt.getBlognameOpt());

				if (name != null && name.getOwner().getAddress().equals(creatorOfDeleteTX))
					deleteBlogPost(db, isShare, blogEntryOpt);
			}
		} else {
			if (StringUtils.isNotBlank(post)) {
				addBlogPost(db, isShare, post, signature, blognameOpt);
			}
		}
	}

	private static void addBlogPost(DBSet db, boolean isShare, String post, byte[] signature, String blognameOpt) {
		// This check also here because we can be called during orphaning
		if (StringUtils.isNotBlank(post)) {
			// Shares won't be hashtagged!
			if (!isShare) {
				List<String> hashTags = BlogUtils.getHashTags(post);

				for (String hashTag : hashTags)
					db.getHashtagPostMap().add(hashTag, signature);
			}

			db.getBlogPostMap().add(blognameOpt, signature);
		}
	}

	private static void deleteBlogPost(DBSet db, boolean isShare, BlogEntry blogEntryOpt) {
		if (isShare) {
			byte[] sharesignature = Base58.decode(blogEntryOpt.getShareSignatureOpt());
			db.getBlogPostMap().remove(blogEntryOpt.getBlognameOpt(), sharesignature);
			db.getSharedPostsMap().remove(sharesignature, blogEntryOpt.getNameOpt());
		} else {
			// removing from hashtagmap
			List<String> hashTags = BlogUtils.getHashTags(blogEntryOpt.getDescription());

			for (String hashTag : hashTags)
				db.getHashtagPostMap().remove(hashTag, Base58.decode(blogEntryOpt.getSignature()));

			db.getBlogPostMap().remove(blogEntryOpt.getBlognameOpt(), Base58.decode(blogEntryOpt.getSignature()));
		}
	}

	public static void processBlogComment(byte[] data, byte[] commentSignature, PublicKeyAccount creator, DBSet db) {
		String string = new String(data, Charsets.UTF_8);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(string);
		if (jsonObject == null)
			return;

		String delete = (String) jsonObject.get(BlogPostResource.DELETE_KEY);

		// CHECK IF THIS IS A DELETE OR CREATE OF A COMMENT
		if (StringUtils.isNotBlank(delete)) {
			BlogEntry commentEntryOpt = BlogUtils.getCommentBlogEntryOpt(delete);

			if (commentEntryOpt == null)
				return;

			String creatorOfDeleteTX = creator.getAddress();
			String creatorOfEntryToDelete = commentEntryOpt.getCreator();

			if (creatorOfDeleteTX.equals(creatorOfEntryToDelete)) {
				// Comment's owner is deleting their own comment
				deleteBlogComment(db, commentEntryOpt);
			} else if (commentEntryOpt.getBlognameOpt() != null) {
				// Blog's [name's] owner is deleting comment
				Name name = db.getNameMap().get(commentEntryOpt.getBlognameOpt());

				if (name != null && name.getOwner().getAddress().equals(creatorOfDeleteTX))
					deleteBlogComment(db, commentEntryOpt);
			}
		} else {
			String post = (String) jsonObject.get(BlogPostResource.POST_KEY);
			String postid = (String) jsonObject.get(BlogPostResource.COMMENT_POSTID_KEY);

			// DOES COMMENT MET MINIMUM CRITERIUM?
			if (StringUtils.isNotBlank(post) && StringUtils.isNotBlank(postid)) {
				addBlogComment(db, post, postid, commentSignature);
			}
		}
	}

	private static void addBlogComment(DBSet db, String post, String postid, byte[] commentSignature) {
		// This check also here because we can be called during orphaning
		if (StringUtils.isNotBlank(post) && StringUtils.isNotBlank(postid)) {
			byte[] postSignature = Base58.decode(postid);
			db.getPostCommentMap().add(postSignature, commentSignature);
			db.getCommentPostMap().add(commentSignature, postSignature);
		}
	}

	private static void deleteBlogComment(DBSet db, BlogEntry commentEntry) {
		byte[] signatureOfComment = Base58.decode(commentEntry.getSignature());
		byte[] signatureOfBlogPostOpt = db.getCommentPostMap().get(signatureOfComment);

		// removing from hashtagmap
		if (signatureOfBlogPostOpt != null) {
			db.getPostCommentMap().remove(signatureOfBlogPostOpt, signatureOfComment);
			db.getCommentPostMap().remove(signatureOfComment);

		}
	}

	public static void addCommentsToBlogEntry(ArbitraryTransaction transaction, BlogEntry blogEntry) {
		if (blogEntry.getBlognameOpt() == null
				|| Profile.getProfileOpt(blogEntry.getBlognameOpt()) != null && Profile.getProfileOpt(blogEntry.getBlognameOpt()).isCommentingAllowed()) {
			PostCommentMap commentPostMap = DBSet.getInstance().getPostCommentMap();
			List<byte[]> comments = commentPostMap.get(transaction.getSignature());

			if (comments == null)
				return;

			for (byte[] commentByteArray : comments) {
				Transaction commentTx = Controller.getInstance().getTransaction(commentByteArray);

				if (commentTx != null) {
					BlogEntry commentBlogEntryOpt = getCommentBlogEntryOpt((ArbitraryTransaction) commentTx);

					if (commentBlogEntryOpt != null)
						blogEntry.addComment(commentBlogEntryOpt);
				}
			}
		}
	}

	public static void orphanBlogPost(byte[] data, byte[] signature, PublicKeyAccount creator, DBSet db) {
		String string = new String(data, Charsets.UTF_8);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(string);
		if (jsonObject == null)
			return;

		String post = (String) jsonObject.get(BlogPostResource.POST_KEY);
		String blognameOpt = (String) jsonObject.get(BlogPostResource.BLOGNAME_KEY);
		String share = (String) jsonObject.get(BlogPostResource.SHARE_KEY);
		String delete = (String) jsonObject.get(BlogPostResource.DELETE_KEY);
		String author = (String) jsonObject.get(BlogPostResource.AUTHOR);

		boolean isShare = false;

		if (StringUtils.isNotEmpty(share)) {
			isShare = true;
			byte[] sharedSignature = Base58.decode(share);

			if (sharedSignature != null)
				db.getSharedPostsMap().remove(sharedSignature, author);
		}

		if (StringUtils.isNotEmpty(delete)) {
			BlogEntry blogEntryOpt = BlogUtils.getBlogEntryOpt(delete);

			if (blogEntryOpt == null)
				return;

			String creatorOfDeleteTX = creator.getAddress();
			String creatorOfEntryToDelete = blogEntryOpt.getCreator();

			if (creatorOfDeleteTX.equals(creatorOfEntryToDelete)) {
				// Post's owner deleted their own post (so recreate it)
				addBlogPost(db, isShare, post, signature, blognameOpt);
			} else if (author != null && blogEntryOpt.getBlognameOpt() != null) {
				// Blog's [name's] owner deleted the post (so recreate it)
				Name name = db.getNameMap().get(blogEntryOpt.getBlognameOpt());

				if (name != null && name.getOwner().getAddress().equals(creatorOfDeleteTX))
					addBlogPost(db, isShare, post, signature, blognameOpt);
			}
		} else {
			if (StringUtils.isNotBlank(post)) {
				BlogEntry blogEntryOpt = BlogUtils.getBlogEntryOpt(signature);

				if (blogEntryOpt == null)
					return;

				// Remove blog post
				deleteBlogPost(db, isShare, blogEntryOpt);
			}
		}
	}

	public static void orphanBlogComment(byte[] data, byte[] commentSignature, PublicKeyAccount creator, DBSet db) {
		String string = new String(data, Charsets.UTF_8);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(string);
		if (jsonObject == null)
			return;

		String delete = (String) jsonObject.get(BlogPostResource.DELETE_KEY);

		// CHECK IF THIS IS A DELETE OR CREATE OF A COMMENT
		if (StringUtils.isNotBlank(delete)) {
			BlogEntry commentEntryOpt = BlogUtils.getCommentBlogEntryOpt(delete);

			if (commentEntryOpt == null)
				return;

			String creatorOfDeleteTX = creator.getAddress();
			String creatorOfEntryToDelete = commentEntryOpt.getCreator();

			if (creatorOfDeleteTX.equals(creatorOfEntryToDelete)) {
				// Comment's owner deleted their own comment (so recreate it)
				String post = (String) jsonObject.get(BlogPostResource.POST_KEY);
				String postid = (String) jsonObject.get(BlogPostResource.COMMENT_POSTID_KEY);

				addBlogComment(db, post, postid, commentSignature);
			} else if (commentEntryOpt.getBlognameOpt() != null) {
				// Blog's [name's] owner is deleted comment (so recreate it)
				Name name = db.getNameMap().get(commentEntryOpt.getBlognameOpt());

				if (name != null && name.getOwner().getAddress().equals(creatorOfDeleteTX)) {
					String post = (String) jsonObject.get(BlogPostResource.POST_KEY);
					String postid = (String) jsonObject.get(BlogPostResource.COMMENT_POSTID_KEY);

					addBlogComment(db, post, postid, commentSignature);
				}
			}
		} else {
			String post = (String) jsonObject.get(BlogPostResource.POST_KEY);
			String postid = (String) jsonObject.get(BlogPostResource.COMMENT_POSTID_KEY);

			// DOES COMMENT MET MINIMUM CRITERIUM?
			if (StringUtils.isNotBlank(post) && StringUtils.isNotBlank(postid)) {
				BlogEntry commentEntryOpt = BlogUtils.getCommentBlogEntryOpt(commentSignature);

				if (commentEntryOpt == null)
					return;

				deleteBlogComment(db, commentEntryOpt);
			}
		}
	}

	public static BlogEntry getBlogEntryOpt(String signature) {
		return getBlogEntryOpt(Base58.decode(signature));
	}

	public static BlogEntry getBlogEntryOpt(byte[] signature) {
		Transaction transaction = Controller.getInstance().getTransaction(signature);

		if (transaction == null || !(transaction instanceof ArbitraryTransaction))
			return null;

		return getBlogEntryOpt((ArbitraryTransaction) transaction);
	}

	public static BlogEntry getCommentBlogEntryOpt(String signatureOfComment) {
		return getCommentBlogEntryOpt(Base58.decode(signatureOfComment));
	}

	public static BlogEntry getCommentBlogEntryOpt(byte[] commentSignature) {
		if (commentSignature == null)
			return null;

		Transaction commentTx = Controller.getInstance().getTransaction(commentSignature);

		if (commentTx == null || !(commentTx instanceof ArbitraryTransaction))
			return null;

		return getCommentBlogEntryOpt((ArbitraryTransaction) commentTx);
	}

	public static BlogEntry getCommentBlogEntryOpt(ArbitraryTransaction transaction) {
		if (transaction.getService() != ArbitraryTransaction.SERVICE_BLOG_COMMENT)
			return null;

		byte[] data = ((ArbitraryTransaction) transaction).getData();
		String string = new String(data, StandardCharsets.UTF_8);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(string);
		if (jsonObject == null)
			return null;

		// MAINBLOG OR CUSTOM BLOG?

		String title = (String) jsonObject.get(BlogPostResource.TITLE_KEY);
		String post = (String) jsonObject.get(BlogPostResource.POST_KEY);
		String nameOpt = (String) jsonObject.get(BlogPostResource.AUTHOR);
		String blognameOpt = (String) jsonObject.get(BlogPostResource.BLOGNAME_KEY);
		String postID = (String) jsonObject.get(BlogPostResource.COMMENT_POSTID_KEY);

		String creator = transaction.getCreator().getAddress();

		if (StringUtil.isNotBlank(post) && StringUtil.isNotBlank(postID)) {
			BlogEntry be = new BlogEntry(title, post, nameOpt, transaction.getTimestamp(), creator, Base58.encode(transaction.getSignature()), blognameOpt);
			be.setCommentPostidOpt(postID);
			return be;
		}

		return null;
	}

	/**
	 * returns blogentry without any restrictions
	 * 
	 * @param transaction
	 * @return
	 */
	// TODO MAYBE JOIN WITH SHARE SO THAT THIS ALSO CONTAINS SHAREDPOSTS!
	public static BlogEntry getBlogEntryOpt(ArbitraryTransaction transaction) {
		if (transaction.getService() != ArbitraryTransaction.SERVICE_BLOG_POST)
			return null;

		byte[] data = ((ArbitraryTransaction) transaction).getData();
		String string = new String(data, StandardCharsets.UTF_8);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(string);
		if (jsonObject == null)
			return null;

		// MAINBLOG OR CUSTOM BLOG?

		String title = (String) jsonObject.get(BlogPostResource.TITLE_KEY);
		String post = (String) jsonObject.get(BlogPostResource.POST_KEY);
		String nameOpt = (String) jsonObject.get(BlogPostResource.AUTHOR);
		String blognameOpt = (String) jsonObject.get(BlogPostResource.BLOGNAME_KEY);
		String share = (String) jsonObject.get(BlogPostResource.SHARE_KEY);

		String creator = transaction.getCreator().getAddress();

		if (StringUtils.isNotEmpty(share)) {
			BlogEntry blogEntryToShareOpt = BlogUtils.getBlogEntryOpt((ArbitraryTransaction) Controller.getInstance().getTransaction(Base58.decode(share)));
			if (blogEntryToShareOpt != null && StringUtils.isNotBlank(blogEntryToShareOpt.getDescription())) {
				// share gets time of sharing!
				blogEntryToShareOpt.setTime(transaction.getTimestamp());
				blogEntryToShareOpt.setShareAuthor(nameOpt != null ? nameOpt : creator);
				blogEntryToShareOpt.setShareSignatureOpt(Base58.encode(transaction.getSignature()));
				addCommentsToBlogEntry(transaction, blogEntryToShareOpt);
				return blogEntryToShareOpt;
			}
		}

		// POST NEEDS TO BE FILLED
		if (StringUtil.isNotBlank(post)) {
			BlogEntry resultBlogEntry = new BlogEntry(title, post, nameOpt, transaction.getTimestamp(), creator, Base58.encode(transaction.getSignature()),
					blognameOpt);
			addCommentsToBlogEntry(transaction, resultBlogEntry);
			return resultBlogEntry;
		}

		return null;
	}

	public static String getCreatorOrBlogOwnerOpt(BlogEntry blogEntryOpt) {
		String creator = blogEntryOpt.getCreator();

		// WE don't have creator account
		if (Controller.getInstance().getAccountByAddress(creator) == null) {
			creator = null;
			String blognameOpt = blogEntryOpt.getBlognameOpt();
			Profile profileOpt = Profile.getProfileOpt(blognameOpt);

			if (profileOpt != null) {
				String blogowner = profileOpt.getName().getOwner().getAddress();

				// are we the owner of the blog?
				if (Controller.getInstance().getAccountByAddress(blogowner) != null)
					creator = blogowner;
			}
		}

		return creator;
	}
}
