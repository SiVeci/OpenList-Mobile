
import React from 'react';
import { Folder, File, FileVideo, FileAudio, FileImage, FileCode, FileArchive, FileText } from 'lucide-react';

interface Props {
  isDir: boolean;
  name: string;
  className?: string;
}

const FileIcon: React.FC<Props> = ({ isDir, name, className = "w-6 h-6" }) => {
  if (isDir) return <Folder className={`${className} text-indigo-400 fill-indigo-400`} />;

  const ext = name.split('.').pop()?.toLowerCase() || '';

  if (['mp4', 'mkv', 'avi', 'mov', 'flv'].includes(ext)) return <FileVideo className={`${className} text-red-500`} />;
  if (['mp3', 'flac', 'wav', 'ogg', 'm4a'].includes(ext)) return <FileAudio className={`${className} text-purple-500`} />;
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return <FileImage className={`${className} text-green-500`} />;
  if (['pdf', 'doc', 'docx', 'txt', 'md'].includes(ext)) return <FileText className={`${className} text-blue-500`} />;
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return <FileArchive className={`${className} text-yellow-600`} />;
  if (['js', 'ts', 'tsx', 'html', 'css', 'json', 'py', 'go'].includes(ext)) return <FileCode className={`${className} text-gray-600`} />;

  return <File className={`${className} text-gray-400`} />;
};

export default FileIcon;
