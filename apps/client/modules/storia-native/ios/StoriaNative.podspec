require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'StoriaNative'
  s.version        = package['version']
  s.summary        = package['description']
  s.author         = ''
  s.homepage       = 'https://github.com/storia-story-chat'
  s.license        = 'MIT'
  s.platforms      = { ios: '15.1' }
  s.source         = { git: '' }
  s.static_framework = true
  s.swift_version  = '5.9'

  s.source_files = '*.{h,m,mm,swift}'

  s.dependency 'React-Core'
end
